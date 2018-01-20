package org.github.mitallast.blkbot.exchanges.binance

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.type.TypeReference
import com.typesafe.config.Config
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import io.vavr.Tuple3
import io.vavr.collection.Vector
import io.vavr.concurrent.Future
import io.vavr.control.Option
import org.apache.logging.log4j.LogManager
import org.github.mitallast.blkbot.common.http.HttpClient
import org.github.mitallast.blkbot.common.http.WebSocketListener
import org.github.mitallast.blkbot.common.json.JsonService
import org.github.mitallast.blkbot.exchanges.ExchangePair
import java.math.BigDecimal
import java.net.URI
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths
import javax.inject.Inject

open class BinanceException(val code: Int, message: String) : RuntimeException(message)
class BinanceClientException(code: Int, message: String) : BinanceException(code, message)
class BinanceServerException(code: Int, message: String) : BinanceException(code, message)
class BinanceLimitException(code: Int, message: String) : BinanceException(code, message)
class BinanceUnknownException(code: Int, message: String) : BinanceException(code, message)

class BinanceLimit private constructor(val value: Int) {
    override fun toString(): String = value.toString()

    fun greaterThan(limit: BinanceLimit): Boolean {
        return value > limit.value
    }

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

interface BinanceListener<in T> {
    fun handle(event: T)
    fun close()
}

annotation class BinanceWeight(val value: Int)

/**
 * See https://github.com/binance-exchange/binance-official-api-docs/blob/master/rest-api.md
 */
class BinanceClient @Inject constructor(
    private val config: Config,
    private val json: JsonService,
    private val http: HttpClient
) {
    private val logger = LogManager.getLogger()

    private val charset = Charset.forName("UTF-8")
    private val endpoint = URI(config.getString("binance.endpoint"))
    private val host = endpoint.host
    private val connect = http.connect(endpoint)

    /**
     * Test connectivity to the Rest API.
     */
    @BinanceWeight(1)
    fun ping(): Future<BinancePong> {
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/api/v1/ping")
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        return send(request).map { response ->
            logger.info("pong")
            response.release()
            BinancePong
        }
    }

    /**
     * Check server time
     */
    @BinanceWeight(1)
    fun time(): Future<BinanceTime> {
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/api/v1/time")
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        return sendJson(request, object : TypeReference<BinanceTime>() {})
    }

    @BinanceWeight(1)
    fun exchangeInfo(): Future<BinanceExchangeInfo> {
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/api/v1/exchangeInfo")
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        return sendJson(request, object : TypeReference<BinanceExchangeInfo>() {})
    }

    /**
     * Order book
     */
    @BinanceWeight(1)
    fun depth(pair: ExchangePair, limit: BinanceLimit? = null): Future<BinanceDepth> {
        if (limit != null && limit.greaterThan(BinanceLimit.limit100)) {
            throw IllegalArgumentException("limit must be 5, 10, 20, 50, 100")
        }
        val uri = "/api/v1/depth?symbol=${pair.symbol()}" +
            Option.of(limit).map { l -> "&limit=${l?.value}" }.getOrElse("")
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri)
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        return sendJson(request, object : TypeReference<BinanceDepthRaw>() {}).map { raw -> raw.map() }
    }

    /**
     * Get recent trades
     */
    @BinanceWeight(1)
    fun trades(pair: ExchangePair, limit: BinanceLimit? = null): Future<Vector<BinanceTrade>> {
        if (limit != null && limit.greaterThan(BinanceLimit.limit500)) {
            throw IllegalArgumentException("limit must be 5, 10, 20, 50, 100, 500")
        }
        val uri = "/api/v1/trades?symbol=${pair.symbol()}" +
            Option.of(limit).map { l -> "&limit=${l?.value}" }.getOrElse("")
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri)
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        return sendJson(request, object : TypeReference<Vector<BinanceTrade>>() {})
    }

    /**
     * Get older trades
     */
    @BinanceWeight(20)
    fun historicalTrades(pair: ExchangePair, fromId: Long? = null, limit: BinanceLimit? = null): Future<Vector<BinanceTrade>> {
        if (limit != null && limit.greaterThan(BinanceLimit.limit500)) {
            throw IllegalArgumentException("limit must be 5, 10, 20, 50, 100, 500")
        }
        val uri = "/api/v1/historicalTrades?symbol=${pair.symbol()}" +
            Option.of(fromId).map { f -> "&fromId=$f" }.getOrElse("") +
            Option.of(limit).map { l -> "&limit=${l?.value}" }.getOrElse("")
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri)
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        return sendJson(request, object : TypeReference<Vector<BinanceTrade>>() {})
    }

    /**
     * Compressed/Aggregate trades list
     *
     * Get compressed, aggregate trades. Trades that fill at the time, from the same order,
     * with the same price will have the quantity aggregated.
     */
    @BinanceWeight(2)
    fun aggTrades(
        pair: ExchangePair,
        fromId: Long? = null,
        startTime: Long? = null,
        endTime: Long? = null,
        limit: BinanceLimit? = null): Future<Vector<BinanceAggTrades>> {
        if (limit != null && limit.greaterThan(BinanceLimit.limit500)) {
            throw IllegalArgumentException("limit must be 5, 10, 20, 50, 100, 500")
        }
        val uri = "/api/v1/aggTrades?symbol=${pair.symbol()}" +
            Option.of(fromId).map { i -> "&fromId=$i" }.getOrElse("") +
            Option.of(startTime).map { i -> "&startTime=$i" }.getOrElse("") +
            Option.of(endTime).map { i -> "&endTime=$i" }.getOrElse("") +
            Option.of(limit).map { i -> "&limit=${i!!.value}" }.getOrElse("")
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri)
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        return sendJson(request, object : TypeReference<Vector<BinanceAggTrades>>() {})
    }

    /**
     * Kline/candlestick bars for a symbol. Klines are uniquely identified by their open time.
     */
    @BinanceWeight(2)
    fun klines(
        pair: ExchangePair,
        interval: BinanceInterval,
        startTime: Long? = null,
        endTime: Long? = null,
        limit: BinanceLimit? = null
    ): Future<Vector<BinanceCandlestick>> {
        if (limit != null && limit.greaterThan(BinanceLimit.limit500)) {
            throw IllegalArgumentException("limit must be 5, 10, 20, 50, 100, 500")
        }
        val uri = "/api/v1/klines?symbol=${pair.symbol()}&interval=${interval.value}" +
            Option.of(startTime).map { i -> "&startTime=$i" }.getOrElse("") +
            Option.of(endTime).map { i -> "&endTime=$i" }.getOrElse("") +
            Option.of(limit).map { i -> "&limit=${i!!.value}" }.getOrElse("")
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri)
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        val ref = object : TypeReference<Vector<Vector<String>>>() {}
        return sendJson(request, ref).map { klines ->
            klines.map { tuple ->
                BinanceCandlestick(
                    tuple[0].toLong(),
                    tuple[1].toBigDecimal(),
                    tuple[2].toBigDecimal(),
                    tuple[3].toBigDecimal(),
                    tuple[4].toBigDecimal(),
                    tuple[5].toBigDecimal(),
                    tuple[6].toLong(),
                    tuple[7].toBigDecimal(),
                    tuple[8].toLong(),
                    tuple[9].toBigDecimal(),
                    tuple[10].toBigDecimal()
                )
            }
        }
    }

    /**
     * 24 hour price change statistics.
     */
    @BinanceWeight(1)
    fun ticker24hr(pair: ExchangePair): Future<BinancePriceChangeStatistics> {
        val uri = "/api/v1/ticker/24hr?symbol=${pair.symbol()}"
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri)
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        return sendJson(request, object : TypeReference<BinancePriceChangeStatistics>() {})
    }

    @BinanceWeight(250)
    fun tickers24hr(): Future<Vector<BinancePriceChangeStatistics>> {
        val uri = "/api/v1/ticker/24hr"
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri)
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        return sendJson(request, object : TypeReference<Vector<BinancePriceChangeStatistics>>() {})
    }

    /**
     * Latest price for all symbols.
     */
    @BinanceWeight(1)
    fun tickerPrices(): Future<Vector<BinanceLastPrice>> {
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/api/v3/ticker/price")
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        return sendJson(request, object : TypeReference<Vector<BinanceLastPrice>>() {})
    }

    /**
     * Latest price for a symbol.
     */
    @BinanceWeight(1)
    fun tickerPrice(pair: ExchangePair): Future<BinanceLastPrice> {
        val uri = "/api/v3/ticker/price?symbol=${pair.symbol()}"
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri)
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        return sendJson(request, object : TypeReference<BinanceLastPrice>() {})
    }

    private fun send(request: HttpRequest): Future<FullHttpResponse> {
        logger.info("request ${request.uri()}")
        return connect.send(request).map { response ->
            val code = response.status().code()
            when (code) {
                in 200..299 -> {
                    logger.info("success")
                    response
                }
                429 -> {
                    val message = response.content().toString(charset)
                    response.release()
                    logger.warn(message)
                    throw BinanceLimitException(code, message)
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
            try {
                val mapped: T = json.deserialize(response.content().markReaderIndex(), type)
                logger.debug("response: {}", mapped)
                mapped
            } catch (e: Exception) {
                val src = response.content().resetReaderIndex().toString(charset)
                val path = Paths.get("binance-${System.currentTimeMillis()}.json")
                Files.write(path, Vector.of(src), charset)
                logger.error("error deserialize: $path", e)
                throw e
            } finally {
                response.release()
            }
        }
    }

    fun depthDataStream(pair: ExchangePair, listener: BinanceListener<EventDepth>) {
        val uri = URI("wss://stream.binance.com:9443/ws/${pair.symbol().toLowerCase()}@depth")
        val type = object : TypeReference<EventDepthRaw>() {}
        val mapper = object : BinanceListener<EventDepthRaw> {
            override fun handle(event: EventDepthRaw) {
                listener.handle(event.map())
            }

            override fun close() {
                listener.close()
            }
        }
        dataStream(uri, type, mapper)
    }

    fun klineDataStream(pair: ExchangePair, interval: BinanceInterval, listener: BinanceListener<EventKLine>) {
        val uri = URI("wss://stream.binance.com:9443/ws/${pair.symbol().toLowerCase()}@kline_${interval.value}")
        val type = object : TypeReference<EventKLine>() {}
        dataStream(uri, type, listener)
    }

    fun tradesDataStream(pair: ExchangePair, listener: BinanceListener<EventTrades>) {
        val uri = URI("wss://stream.binance.com:9443/ws/${pair.symbol().toLowerCase()}@aggTrade")
        val type = object : TypeReference<EventTrades>() {}
        dataStream(uri, type, listener)
    }

    private fun <T> dataStream(uri: URI, type: TypeReference<T>, listener: BinanceListener<T>) {
        val webSocketListener = object : WebSocketListener {
            override fun handle(frame: WebSocketFrame) {
                when (frame) {
                    is TextWebSocketFrame -> {
                        val event = json.deserialize(frame.content(), type)
                        logger.debug("event: $event")
                        listener.handle(event)
                    }
                }
            }

            override fun close() {
                logger.info("stream closed")
                listener.close()
            }
        }
        http.websocket(uri, webSocketListener)
    }
}

object BinancePong

data class BinanceTime(val serverTime: Long)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BinanceExchangeInfo(
    val timezone: String,
    val serverTime: Long,
    val rateLimits: Vector<BinanceRateLimitInfo>,
    val symbols: Vector<BinanceSymbolInfo>
)

data class BinanceRateLimitInfo(
    val rateLimitType: String,
    val interval: String,
    val limit: Long
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BinanceSymbolInfo(
    val symbol: String,
    val status: String,
    val baseAsset: String,
    val baseAssetPrecision: Int,
    val quoteAsset: String,
    val quotePrecision: Int,
    val orderTypes: Vector<String>,
    val icebergAllowed: Boolean
)


data class BinanceDepthRaw(
    val lastUpdateId: Long,
    val bids: Vector<Tuple3<BigDecimal, BigDecimal, List<String>>>,
    val asks: Vector<Tuple3<BigDecimal, BigDecimal, List<String>>>
) {
    fun map(): BinanceDepth {
        return BinanceDepth(
            lastUpdateId,
            bids.map { bid -> BinanceDepthBid(bid._1, bid._2) },
            asks.map { ask -> BinanceDepthAsk(ask._1, ask._2) }
        )
    }
}

data class BinanceDepthBid(val price: BigDecimal, val quantity: BigDecimal) {
    override fun toString(): String = "{$price,$quantity}"
}

data class BinanceDepthAsk(val price: BigDecimal, val quantity: BigDecimal) {
    override fun toString(): String = "{$price,$quantity}"
}

data class BinanceDepth(val lastUpdateId: Long, val bids: Vector<BinanceDepthBid>, val asks: Vector<BinanceDepthAsk>)

data class BinanceTrade(
    val id: Long,
    val price: BigDecimal,
    val qty: BigDecimal,
    val time: Long,
    val isBuyerMaker: Boolean,
    val isBestMatch: Boolean
)

data class BinanceAggTrades(
    @JsonProperty("a") val tradeId: Long,
    @JsonProperty("p") val price: BigDecimal,
    @JsonProperty("q") val quantity: BigDecimal,
    @JsonProperty("f") val firstTradeId: Long,
    @JsonProperty("l") val lastTradeId: Long,
    @JsonProperty("T") val timestamp: Long,
    @JsonProperty("m") val isBuyerMaker: Boolean,
    @JsonProperty("M") val isTradeBestPriceMatch: Boolean
)

data class BinanceCandlestick(
    val openTime: Long,
    val open: BigDecimal,
    val high: BigDecimal,
    val low: BigDecimal,
    val close: BigDecimal,
    val volume: BigDecimal,
    val closeTime: Long,
    val quoteAssetVolume: BigDecimal,
    val numberOfTrades: Long,
    val takerBuyBaseAssetVolume: BigDecimal,
    val takerBuyQuoteAssetVolume: BigDecimal
)

data class BinancePriceChangeStatistics(
    val symbol: String,
    val priceChange: BigDecimal,
    val priceChangePercent: BigDecimal,
    val weightedAvgPrice: BigDecimal,
    val prevClosePrice: BigDecimal,
    val lastPrice: BigDecimal,
    val lastQty: BigDecimal,
    val bidPrice: BigDecimal,
    val bidQty: BigDecimal,
    val askPrice: BigDecimal,
    val askQty: BigDecimal,
    val openPrice: BigDecimal,
    val highPrice: BigDecimal,
    val lowPrice: BigDecimal,
    val volume: BigDecimal,
    val quoteVolume: BigDecimal,
    val openTime: Long,
    val closeTime: Long,
    val firstId: Long,
    val lastId: Long,
    val count: Long
)

data class BinanceLastPrice(val symbol: String, val price: BigDecimal)

data class EventDepthRaw(
    @JsonProperty("e") val eventType: String,
    @JsonProperty("E") val eventTime: Long,
    @JsonProperty("s") val symbol: String,
    @JsonProperty("u") val updateId: Long,
    @JsonProperty("b") val bids: Vector<Tuple3<BigDecimal, BigDecimal, List<String>>>,
    @JsonProperty("a") val asks: Vector<Tuple3<BigDecimal, BigDecimal, List<String>>>
) {
    fun map(): EventDepth = EventDepth(
        eventTime = eventTime,
        symbol = symbol,
        updateId = updateId,
        bids = bids.map { bid -> BinanceDepthBid(bid._1, bid._2) },
        asks = asks.map { ask -> BinanceDepthAsk(ask._1, ask._2) }
    )
}

data class EventDepth(
    val eventTime: Long,
    val symbol: String,
    val updateId: Long,
    val bids: Vector<BinanceDepthBid>,
    val asks: Vector<BinanceDepthAsk>
)

data class EventKLineData(
    @JsonProperty("t") val startTime: Long,
    @JsonProperty("T") val endTime: Long,
    @JsonProperty("s") val symbol: String,
    @JsonProperty("i") val interval: String,
    @JsonProperty("f") val firstTradeId: Long,
    @JsonProperty("L") val lastTradeId: Long,
    @JsonProperty("o") val open: BigDecimal,
    @JsonProperty("c") val close: BigDecimal,
    @JsonProperty("h") val high: BigDecimal,
    @JsonProperty("l") val low: BigDecimal,
    @JsonProperty("v") val volume: BigDecimal,
    @JsonProperty("n") val numberOfTrades: Long,
    @JsonProperty("x") val isBarFinal: Boolean,
    @JsonProperty("q") val quoteVolume: BigDecimal,
    @JsonProperty("V") val volumeOfActiveBuy: BigDecimal,
    @JsonProperty("Q") val quoteVolumeOfActiveBuy: BigDecimal,
    @JsonProperty("B") val ignored: BigDecimal
)

data class EventKLine(
    @JsonProperty("e") val eventType: String,
    @JsonProperty("E") val eventTime: Long,
    @JsonProperty("s") val symbol: String,
    @JsonProperty("k") val data: EventKLineData
)

data class EventTrades(
    @JsonProperty("e") val eventType: String,
    @JsonProperty("E") val eventTime: Long,
    @JsonProperty("s") val symbol: String,
    @JsonProperty("a") val aggTradeId: Long,
    @JsonProperty("p") val price: BigDecimal,
    @JsonProperty("q") val quantity: BigDecimal,
    @JsonProperty("f") val firstBreakdownTradeId: Long,
    @JsonProperty("l") val lastBreakdownTradeId: Long,
    @JsonProperty("T") val tradeTime: Long,
    @JsonProperty("m") val buyerIsMaker: Boolean,
    @JsonProperty("M") val ignore: Boolean
)