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
import java.util.concurrent.TimeUnit
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import io.vavr.concurrent.Future
import io.vavr.concurrent.Promise
import org.apache.logging.log4j.LogManager
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentLinkedDeque

class HttpClient(uri: URI, config: Config, provider: NettyProvider) {
    private val logger = LogManager.getLogger()

    private val maxContentLength: Int = config.getInt("http.max_content_length")
    private val keepAlive: Boolean = config.getBoolean("http.keep_alive")
    private val reuseAddress: Boolean = config.getBoolean("http.reuse_address")
    private val tcpNoDelay: Boolean = config.getBoolean("http.tcp_no_delay")
    private val sndBuf: Int = config.getInt("http.snd_buf")
    private val rcvBuf: Int = config.getInt("http.rcv_buf")
    private val connectTimeout: Long = config.getDuration("http.connect_timeout", TimeUnit.MILLISECONDS)

    private val queue = ConcurrentLinkedDeque<Promise<FullHttpResponse>>()

    private val ssl: Boolean
    private val host: String
    private val port: Int
    private val sslCtx: SslContext?
    private val bootstrap: Bootstrap
    private val channel: Channel

    init {
        ssl = when {
            "https".equals(uri.scheme, ignoreCase = true) -> true
            "http".equals(uri.scheme, ignoreCase = true) -> false
            else -> throw IllegalArgumentException("http/s only")
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
                .handler(HttpClientChannelInitializer())

        val future = bootstrap.connect(uri.host, port)
        future.awaitUninterruptibly(connectTimeout, TimeUnit.MILLISECONDS)
        if(!future.isDone) {
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

    inner class HttpClientChannelInitializer() : ChannelInitializer<Channel>() {
        override fun initChannel(ch: Channel) {
            val p = ch.pipeline()
            if (sslCtx != null) {
                logger.info("use ssl {}", sslCtx)
                p.addLast(sslCtx.newHandler(ch.alloc()))
            }
            p.addLast(HttpClientCodec())
            p.addLast(HttpContentDecompressor())
            p.addLast(HttpObjectAggregator(maxContentLength));
            p.addLast(HttpClientChannelHandler())
        }
    }

    inner class HttpClientChannelHandler() : SimpleChannelInboundHandler<FullHttpResponse>(false) {
        override fun channelRead0(ctx: ChannelHandlerContext, response: FullHttpResponse) {
            logger.debug("response: {}", response)
            queue.poll().success(response)
        }

        @Suppress("OverridingDeprecatedMember")
        override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
            logger.error("unexpected error {}", ctx, cause)
            while (queue.isNotEmpty()) {
                queue.poll().failure(cause)
            }
            ctx.close()
        }
    }

    fun send(request: HttpRequest): Future<FullHttpResponse> {
        logger.debug("send: {}", request)
        val promise = Promise.make<FullHttpResponse>()
        queue.push(promise)
        channel.writeAndFlush(request)
        return promise.future()
    }
}
