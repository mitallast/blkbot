package org.github.mitallast.blkbot.common.http

import com.typesafe.config.Config
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.*
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.websocketx.*
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory.newHandshaker
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.util.CharsetUtil
import io.vavr.concurrent.Promise
import org.apache.logging.log4j.LogManager
import org.github.mitallast.blkbot.common.netty.NettyProvider
import java.net.URI
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit

interface WebSocketListener {
    fun handle(frame: WebSocketFrame)
    fun close()
}

class WebSocketClient(val uri: URI, val listener: WebSocketListener, config: Config, provider: NettyProvider) {
    private val logger = LogManager.getLogger()

    private val maxContentLength: Int = config.getInt("http.max_content_length")
    private val keepAlive: Boolean = config.getBoolean("http.keep_alive")
    private val reuseAddress: Boolean = config.getBoolean("http.reuse_address")
    private val tcpNoDelay: Boolean = config.getBoolean("http.tcp_no_delay")
    private val sndBuf: Int = config.getInt("http.snd_buf")
    private val rcvBuf: Int = config.getInt("http.rcv_buf")
    private val connectTimeout: Long = config.getDuration("http.connect_timeout", TimeUnit.MILLISECONDS)

    private val ssl: Boolean
    private val host: String
    private val port: Int
    private val sslCtx: SslContext?
    private val bootstrap: Bootstrap
    private val channel: Channel

    init {
        ssl = when {
            "wss".equals(uri.scheme, ignoreCase = true) -> true
            "ws".equals(uri.scheme, ignoreCase = true) -> false
            else -> throw IllegalArgumentException("ws/s only")
        }
        host = uri.host
        port = when {
            uri.port != -1 -> uri.port
            ssl -> 443
            else -> 80
        }
        sslCtx = if (ssl) {
            SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build()
        } else {
            null
        }
        bootstrap = Bootstrap()
                .channel(provider.clientChannel())
                .group(provider.child())
                .option(ChannelOption.SO_REUSEADDR, reuseAddress)
                .option(ChannelOption.SO_KEEPALIVE, keepAlive)
                .option(ChannelOption.TCP_NODELAY, tcpNoDelay)
                .option(ChannelOption.SO_SNDBUF, sndBuf)
                .option(ChannelOption.SO_RCVBUF, rcvBuf)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout.toInt())
                .option<ByteBufAllocator>(ChannelOption.ALLOCATOR, PooledByteBufAllocator(true))
                .option<RecvByteBufAllocator>(ChannelOption.RCVBUF_ALLOCATOR, FixedRecvByteBufAllocator(65536))
                .handler(WebSocketClientChannelInitializer())

        val future = bootstrap.connect(uri.host, port)
        future.awaitUninterruptibly(connectTimeout, TimeUnit.MILLISECONDS)
        if (!future.isDone) {
            future.cancel(true)
        }
        if (future.isCancelled) {
            // Connection attempt cancelled by user
            throw CancellationException("connection is canceled")
        } else if (!future.isSuccess) {
            throw future.cause()
        } else {
            channel = future.channel()
        }
    }

    inner class WebSocketClientChannelInitializer() : ChannelInitializer<Channel>() {
        override fun initChannel(ch: Channel) {
            val p = ch.pipeline()
            if (sslCtx != null) {
                logger.info("use ssl {}", sslCtx)
                p.addLast(sslCtx.newHandler(ch.alloc()))
            }
            p.addLast(HttpClientCodec())
            p.addLast(HttpObjectAggregator(maxContentLength));
            p.addLast(WebSocketClientCompressionHandler.INSTANCE);
            p.addLast(WebSocketClientChannelHandler())
        }
    }

    inner class WebSocketClientChannelHandler : SimpleChannelInboundHandler<Any>() {
        private val handshaker = newHandshaker(uri, WebSocketVersion.V13, null, true, DefaultHttpHeaders());
        private var handshakeFuture = Promise.make<Unit>()

        override fun channelActive(ctx: ChannelHandlerContext) {
            handshaker.handshake(ctx.channel())
        }

        override fun channelInactive(ctx: ChannelHandlerContext) {
            logger.warn("websocket client disconnected")
            listener.close()
        }

        override fun channelRead0(ctx: ChannelHandlerContext, msg: Any) {
            logger.debug("response: {}", msg)
            val ch = ctx.channel()
            if (!handshaker.isHandshakeComplete) {
                try {
                    handshaker.finishHandshake(ch, msg as FullHttpResponse)
                    logger.warn("WebSocket Client connected!")
                    handshakeFuture.success(Unit)
                } catch (e: WebSocketHandshakeException) {
                    logger.warn("WebSocket Client failed to connect")
                    handshakeFuture.failure(e)
                    ch.close()
                }
                return
            }
            if (msg is FullHttpResponse) {
                throw IllegalStateException(
                        "Unexpected FullHttpResponse (getStatus=" + msg.status() +
                                ", content=" + msg.content().toString(CharsetUtil.UTF_8) + ')')
            }

            val frame = msg as WebSocketFrame
            when (frame) {
                is TextWebSocketFrame -> {
                    logger.info("websocket client received message: " + frame.text())
                    listener.handle(frame)
                }
                is BinaryWebSocketFrame -> {
                    logger.info("websocket client received binary message")
                    listener.handle(frame)
                }
                is PongWebSocketFrame -> {
                    logger.info("websocket client received pong")
                }
                is CloseWebSocketFrame -> {
                    logger.info("websocket client received closing")
                    ch.close()
                }
            }
        }

        @Suppress("OverridingDeprecatedMember")
        override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
            logger.error("unexpected error {}", ctx, cause)
            if (!handshakeFuture.isCompleted) {
                handshakeFuture.tryFailure(cause)
            }
            ctx.close();
        }
    }
}