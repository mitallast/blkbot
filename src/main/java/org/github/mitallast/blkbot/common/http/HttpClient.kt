package org.github.mitallast.blkbot.common.http

import com.typesafe.config.Config
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.*
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import org.github.mitallast.blkbot.common.netty.NettyProvider
import java.net.URI
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.pool.ChannelPoolHandler
import io.netty.channel.pool.FixedChannelPool
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http.websocketx.*
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler
import io.netty.util.AttributeKey
import io.netty.util.CharsetUtil
import io.netty.util.concurrent.Future as NFuture
import io.vavr.concurrent.Future
import io.vavr.concurrent.Promise
import org.apache.logging.log4j.LogManager
import java.io.Closeable
import java.nio.channels.ClosedChannelException
import java.util.concurrent.*
import javax.inject.Inject

interface HttpHostClient : Closeable {
    fun send(request: HttpRequest): Future<FullHttpResponse>
}

interface WebSocketListener : Closeable {
    fun handle(frame: WebSocketFrame)
}

interface WebSocketClient : Closeable {
}

class HttpClient @Inject constructor(config: Config, private val provider: NettyProvider) {
    private val logger = LogManager.getLogger()

    private val maxContentLength: Int = config.getInt("http.max_content_length")
    private val keepAlive: Boolean = config.getBoolean("http.keep_alive")
    private val reuseAddress: Boolean = config.getBoolean("http.reuse_address")
    private val tcpNoDelay: Boolean = config.getBoolean("http.tcp_no_delay")
    private val sndBuf: Int = config.getInt("http.snd_buf")
    private val rcvBuf: Int = config.getInt("http.rcv_buf")
    private val connectTimeout: Long = config.getDuration("http.connect_timeout", TimeUnit.MILLISECONDS)

    private val sslCtx: SslContext = SslContextBuilder.forClient()
        .trustManager(InsecureTrustManagerFactory.INSTANCE)
        .build()

    private val queueAttr = AttributeKey.valueOf<ArrayBlockingQueue<Promise<FullHttpResponse>>>("queue")

    fun connect(uri: URI): HttpHostClient = PooledHttpHostClient(uri)

    fun websocket(uri: URI, listener: WebSocketListener): WebSocketClient = SimpleWebSocketClient(uri, listener)

    private fun bootstrap(): Bootstrap {
        return Bootstrap()
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
    }

    private inner class PooledHttpHostClient constructor(uri: URI) : HttpHostClient {
        private val ssl = when {
            "https".equals(uri.scheme, ignoreCase = true) -> true
            "http".equals(uri.scheme, ignoreCase = true) -> false
            else -> throw IllegalArgumentException("http/s only")
        }
        private val host = uri.host
        private val port = when {
            uri.port != -1 -> uri.port
            ssl -> 443
            else -> 80
        }
        private val bootstrap = bootstrap().remoteAddress(host, port)
        private val poolHandler = object : ChannelPoolHandler {
            override fun channelReleased(ch: Channel) {}

            override fun channelAcquired(ch: Channel) {}

            override fun channelCreated(ch: Channel) {
                ch.attr(queueAttr).set(ArrayBlockingQueue(1024))
                val p = ch.pipeline()
                if (ssl) {
                    logger.info("use ssl")
                    p.addLast(sslCtx.newHandler(ch.alloc()))
                }
                p.addLast(HttpClientCodec())
                p.addLast(HttpContentDecompressor())
                p.addLast(HttpObjectAggregator(maxContentLength));
                p.addLast(HttpClientChannelHandler())
            }
        }
        private val pool = FixedChannelPool(bootstrap, poolHandler, 1, 1)

        override fun send(request: HttpRequest): Future<FullHttpResponse> {
            logger.debug("send: {}", request)
            val promise = Promise.make<FullHttpResponse>()
            val future = pool.acquire()
            future.addListener { f ->
                when {
                    f.isCancelled -> {
                        logger.error("pool channel acquire is canceled")
                        promise.failure(CancellationException("pool cancel acquire"))
                    }
                    f.isSuccess -> {
                        val ch = f.now as Channel
                        try {
                            val q = ch.attr(queueAttr).get()
                            if (q.remainingCapacity() == 0) {
                                promise.failure(IllegalStateException("queue overflow"))
                            } else {
                                ch.writeAndFlush(request, ch.voidPromise())
                                q.offer(promise)
                            }
                        } catch (e: Exception) {
                            logger.error("unexpected error", e)
                            promise.failure(e)
                        } finally {
                            pool.release(ch)
                        }
                    }
                    else -> {
                        logger.error("error acquire {}", f.cause())
                        promise.failure(f.cause())
                    }
                }
            }
            return promise.future()
        }

        override fun close() {
            pool.close()
        }

        private inner class HttpClientChannelHandler : SimpleChannelInboundHandler<FullHttpResponse>(false) {
            override fun channelInactive(ctx: ChannelHandlerContext) {
                val cause = ClosedChannelException()
                val queue = ctx.channel().attr(queueAttr).get()
                while (queue.isNotEmpty()) {
                    queue.poll().failure(cause)
                }
            }

            override fun channelRead0(ctx: ChannelHandlerContext, response: FullHttpResponse) {
                val queue = ctx.channel().attr(queueAttr).get()
                logger.debug("response: {}", response)
                queue.poll().success(response)
            }

            @Suppress("OverridingDeprecatedMember")
            override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
                logger.error("unexpected error {}", ctx, cause)
                ctx.close()
                val queue = ctx.channel().attr(queueAttr).get()
                while (queue.isNotEmpty()) {
                    queue.poll().failure(cause)
                }
            }
        }
    }

    private inner class SimpleWebSocketClient constructor(private val uri: URI, private val listener: WebSocketListener) : WebSocketClient {
        private val ssl = when {
            "wss".equals(uri.scheme, ignoreCase = true) -> true
            "ws".equals(uri.scheme, ignoreCase = true) -> false
            else -> throw IllegalArgumentException("ws/s only")
        }

        private val host = uri.host

        private val port = when {
            uri.port != -1 -> uri.port
            ssl -> 443
            else -> 80
        }

        private val bootstrap: Bootstrap = bootstrap()
            .remoteAddress(host, port)
            .handler(WebSocketClientChannelInitializer())

        private val channel: Channel

        init {
            val future = bootstrap.connect()
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

        override fun close() {
            if (channel.isOpen) {
                channel.close()
            }
        }

        private inner class WebSocketClientChannelInitializer : ChannelInitializer<Channel>() {
            override fun initChannel(ch: Channel) {
                val p = ch.pipeline()
                if (ssl) {
                    logger.info("use ssl {}", sslCtx)
                    p.addLast(sslCtx.newHandler(ch.alloc()))
                }
                p.addLast(HttpClientCodec())
                p.addLast(HttpObjectAggregator(maxContentLength));
                p.addLast(WebSocketClientCompressionHandler.INSTANCE);
                p.addLast(WebSocketClientChannelHandler())
            }
        }

        private inner class WebSocketClientChannelHandler : SimpleChannelInboundHandler<Any>() {
            private val handshaker = WebSocketClientHandshakerFactory.newHandshaker(uri, WebSocketVersion.V13, null, true, DefaultHttpHeaders());
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
}
