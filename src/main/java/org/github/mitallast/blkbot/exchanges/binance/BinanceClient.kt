package org.github.mitallast.blkbot.exchanges.binance

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.type.TypeReference
import com.typesafe.config.Config
import io.netty.handler.codec.http.*
import io.vavr.Tuple3
import io.vavr.collection.Vector
import io.vavr.concurrent.Future
import io.vavr.control.Option
import org.apache.logging.log4j.LogManager
import org.github.mitallast.blkbot.common.http.HttpClient
import org.github.mitallast.blkbot.common.json.JsonService
import org.github.mitallast.blkbot.common.netty.NettyProvider
import org.github.mitallast.blkbot.exchanges.ExchangePair
import java.net.URI
import java.nio.charset.Charset
import javax.inject.Inject

open class BinanceException(val code: Int, message: String) : RuntimeException(message)
class BinanceClientException(code: Int, message: String) : BinanceException(code, message)
class BinanceServerException(code: Int, message: String) : BinanceException(code, message)
class BinanceUnknownException(code: Int, message: String) : BinanceException(code, message)

class BinanceLimit private constructor(val value: Int) {
    override fun toString(): String = value.toString()

    companion object {
        val limit5 = BinanceLimit(5)
        val limit10 = BinanceLimit(10)
        val limit20 = BinanceLimit(20)
        val limit50 = BinanceLimit(50)
        val limit100 = BinanceLimit(100)
        val limit500 = BinanceLimit(500)
        val limit1000 = BinanceLimit(1000)
    }
}

class BinanceInterval private constructor(val value: String) {
    companion object {
        val int1m = BinanceInterval("1m")
        val int3m = BinanceInterval("3m")
        val int5m = BinanceInterval("5m")
        val int15m = BinanceInterval("15m")
        val int30m = BinanceInterval("30m")
        val int1h = BinanceInterval("1h")
        val int2h = BinanceInterval("2h")
        val int4h = BinanceInterval("4h")
        val int6h = BinanceInterval("6h")
        val int8h = BinanceInterval("8h")
        val int12h = BinanceInterval("12h")
        val int1d = BinanceInterval("1d")
        val int3d = BinanceInterval("3d")
        val int1w = BinanceInterval("1w")
        val int1M = BinanceInterval("1M")
    }
}

class BinanceClient @Inject constructor(
        config: Config,
        provider: NettyProvider,
        private val json: JsonService
) {
    private val logger = LogManager.getLogger()

    private val charset = Charset.forName("UTF-8")
    private val endpoint = URI(config.getString("binance.endpoint"))
    private val host = endpoint.host
    private val http = HttpClient(endpoint, config, provider)

    /**
     * Test connectivity to the Rest API.
     */
    fun ping(): Future<ResponsePong> {
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/api/v1/ping")
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        return send(request).map { response ->
            logger.info("pong")
            response.release()
            ResponsePong
        }
    }

    /**
     * Check server time
     */
    fun time(): Future<ResponseTime> {
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/api/v1/time")
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        return sendJson(request, object : TypeReference<ResponseTime>() {})
    }

    /**
     * Order book
     */
    fun depth(pair: ExchangePair, limit: BinanceLimit = BinanceLimit.limit100): Future<ResponseDepth> {
        val uri = "/api/v1/depth?symbol=${pair.symbol()}&limit=${limit.value}"
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri)
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        return sendJson(request, object : TypeReference<ResponseDepthRaw>() {}).map { raw -> raw.map() }
    }

    /**
     * Compressed/Aggregate trades list
     *
     * Get compressed, aggregate trades. Trades that fill at the time, from the same order,
     * with the same price will have the quantity aggregated.
     */
    fun aggTrades(
            pair: ExchangePair,
            fromId: Option<Long> = Option.none(),
            startTime: Option<Long> = Option.none(),
            endTime: Option<Long> = Option.none(),
            limit: Option<BinanceLimit> = Option.none()): Future<Vector<ResponseAggTrades>> {
        val uri = "/api/v1/aggTrades?symbol=${pair.symbol()}" +
                fromId.map { i -> "&fromId=$i" }.getOrElse("") +
                startTime.map { i -> "&startTime=$i" }.getOrElse("") +
                endTime.map { i -> "&endTime=$i" }.getOrElse("") +
                limit.map { i -> "&limit=${i.value}" }.getOrElse("")
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri)
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        return sendJson(request, object : TypeReference<Vector<ResponseAggTrades>>() {})
    }

    /**
     * Kline/candlestick bars for a symbol. Klines are uniquely identified by their open time.
     */
    fun klines(
            pair: ExchangePair,
            interval: BinanceInterval,
            startTime: Option<Long> = Option.none(),
            endTime: Option<Long> = Option.none(),
            limit: Option<BinanceLimit> = Option.none()
            ): Future<Vector<ResponseCandlestick>> {
        val uri = "/api/v1/klines?symbol=${pair.symbol()}&interval=${interval.value}" +
                startTime.map { i -> "&startTime=$i" }.getOrElse("") +
                endTime.map { i -> "&endTime=$i" }.getOrElse("") +
                limit.map { i -> "&limit=${i.value}" }.getOrElse("")
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri)
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        val ref = object : TypeReference<Vector<Vector<String>>>() {}
        return sendJson(request, ref).map { klines ->
            klines.map { tuple ->
                ResponseCandlestick(
                        tuple[0].toLong(),
                        tuple[1].toDouble(),
                        tuple[2].toDouble(),
                        tuple[3].toDouble(),
                        tuple[4].toDouble(),
                        tuple[5].toDouble(),
                        tuple[6].toLong(),
                        tuple[7].toDouble(),
                        tuple[8].toLong(),
                        tuple[9].toDouble(),
                        tuple[10].toDouble()
                )
            }
        }
    }

    /**
     * 24 hour price change statistics.
     */
    fun ticker24hr(pair: ExchangePair): Future<ResponsePriceChangeStatistics> {
        val uri = "/api/v1/ticker/24hr?symbol=${pair.symbol()}"
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri)
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        return sendJson(request, object : TypeReference<ResponsePriceChangeStatistics>() {})
    }

    /**
     * Latest price for all symbols.
     */
    fun allPrices() : Future<Vector<ResponseLatestPrice>> {
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/api/v1/ticker/allPrices")
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        return sendJson(request, object : TypeReference<Vector<ResponseLatestPrice>>() {})
    }

    /**
     * Best price/qty on the order book for all symbols
     */
    fun allBookTickers() : Future<Vector<ResponseBestPriceQty>> {
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/api/v1/ticker/allBookTickers")
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        return sendJson(request, object : TypeReference<Vector<ResponseBestPriceQty>>() {})
    }

    private fun send(request: HttpRequest): Future<FullHttpResponse> {
        logger.info("request ${request.uri()}")
        return http.send(request).map { response ->
            val code = response.status().code()
            when (code) {
                in 200..299 -> {
                    logger.info("success")
                    response
                }
                in 400..499 -> {
                    val message = response.content().toString(charset)
                    response.release()
                    logger.warn(message)
                    throw BinanceClientException(code, message)
                }
                504 -> {
                    val message = response.content().toString(charset)
                    response.release()
                    logger.warn(message)
                    throw BinanceUnknownException(code, message)
                }
                in 500..599 -> {
                    val message = response.content().toString(charset)
                    response.release()
                    logger.warn(message)
                    throw BinanceServerException(code, message)
                }
                else -> {
                    val message = response.content().toString(charset)
                    response.release()
                    logger.warn(message)
                    throw BinanceUnknownException(code, message)
                }
            }
        }
    }

    private fun <T> sendJson(request: HttpRequest, type: TypeReference<T>): Future<T> {
        return send(request).map { response: FullHttpResponse ->
            logger.info("deserialize {}", response.content().toString(charset))
            val mapped: T = json.deserialize(response.content(), type)
            logger.info("response: {}", mapped)
            response.release()
            mapped
        }
    }
}

object ResponsePong
data class ResponseTime(val serverTime: Long)
data class ResponseDepthRaw(
        val lastUpdateId: Long,
        val bids: Vector<Tuple3<Double, Double, List<String>>>,
        val asks: Vector<Tuple3<Double, Double, List<String>>>
) {
    fun map(): ResponseDepth {
        return ResponseDepth(
                lastUpdateId,
                bids.map { bid -> ResponseDepthBid(bid._1, bid._2) },
                asks.map { ask -> ResponseDepthAsk(ask._1, ask._2) }
        )
    }
}

data class ResponseDepthBid(val price: Double, val quantity: Double) {
    override fun toString(): String = "{$price,$quantity}"
}

data class ResponseDepthAsk(val price: Double, val quantity: Double) {
    override fun toString(): String = "{$price,$quantity}"
}

data class ResponseDepth(val lastUpdateId: Long, val bids: Vector<ResponseDepthBid>, val asks: Vector<ResponseDepthAsk>)

data class ResponseAggTrades(
        @JsonProperty("a")
        val tradeId: Long,
        @JsonProperty("p")
        val price: Double,
        @JsonProperty("q")
        val quantity: Double,
        @JsonProperty("f")
        val firstTradeId: Long,
        @JsonProperty("l")
        val lastTradeId: Long,
        @JsonProperty("T")
        val timestamp: Long,
        @JsonProperty("m")
        val isBuyerMaker: Boolean,
        @JsonProperty("M")
        val isTradeBestPriceMatch: Boolean
)

data class ResponseCandlestick(
        val openTime: Long,
        val open: Double,
        val high: Double,
        val low: Double,
        val close: Double,
        val volume: Double,
        val closeTime: Long,
        val quoteAssetVolume: Double,
        val numberOfTrades: Long,
        val takerBuyBaseAssetVolume: Double,
        val takerBuyQuoteAssetVolume: Double
)

data class ResponsePriceChangeStatistics(
        val symbol: String,
        val priceChange: Double,
        val priceChangePercent: Double,
        val weightedAvgPrice: Double,
        val prevClosePrice: Double,
        val lastPrice: Double,
        val lastQty: Double,
        val bidPrice: Double,
        val bidQty: Double,
        val askPrice: Double,
        val askQty: Double,
        val openPrice: Double,
        val highPrice: Double,
        val lowPrice: Double,
        val volume: Double,
        val quoteVolume: Double,
        val openTime: Long,
        val closeTime: Long,
        val firstId: Long,
        val lastId: Long,
        val count: Long
)

data class ResponseLatestPrice(val symbol: String, val price: Double)
data class ResponseBestPriceQty(
        val symbol: String,
        val bidPrice: Double,
        val bidQty: Double,
        val askPrice: Double,
        val askQty: Double
)