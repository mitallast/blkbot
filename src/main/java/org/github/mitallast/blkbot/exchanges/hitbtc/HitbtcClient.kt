package org.github.mitallast.blkbot.exchanges.hitbtc

import com.fasterxml.jackson.core.type.TypeReference
import com.typesafe.config.Config
import io.netty.handler.codec.http.*
import io.vavr.collection.Vector
import io.vavr.concurrent.Future
import io.vavr.control.Option
import org.apache.logging.log4j.LogManager
import org.github.mitallast.blkbot.common.http.HttpClient
import org.github.mitallast.blkbot.common.json.JsonService
import org.github.mitallast.blkbot.exchanges.ExchangePair
import org.joda.time.DateTime
import java.math.BigDecimal
import java.net.URI
import java.nio.charset.Charset
import javax.inject.Inject

open class HitbtcException(val code: Int, message: String) : RuntimeException(message)
class HitbtcClientException(code: Int, message: String) : HitbtcException(code, message)
class HitbtcServerException(code: Int, message: String) : HitbtcException(code, message)
class HitbtcUnknownException(code: Int, message: String) : HitbtcException(code, message)

/**
 * See https://api.hitbtc.com/
 */
class HitbtcClient @Inject constructor(
    private val config: Config,
    private val json: JsonService,
    private val http: HttpClient
) {
    private val logger = LogManager.getLogger()

    private val charset = Charset.forName("UTF-8")
    private val endpoint = URI(config.getString("hitbtc.endpoint"))
    private val host = endpoint.host
    private val connect = http.connect(endpoint)

    /**
     * Return the actual list of available currencies, tokens, ICO etc.
     */
    fun currencies(): Future<Vector<HitbtcCurrency>> {
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/api/2/public/currency")
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        val type = object : TypeReference<Vector<HitbtcCurrency>>() {}
        return sendJson(request, type)
    }

    fun currency(id: String): Future<HitbtcCurrency> {
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/api/2/public/currency/$id")
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        val type = object : TypeReference<HitbtcCurrency>() {}
        return sendJson(request, type)
    }

    /**
     * Return the actual list of currency symbols (currency pairs) traded on HitBTC exchange.
     * The first listed currency of a symbol is called the base currency, and the second currency is called
     * the quote currency. The currency pair indicates how much of the quote currency is needed to purchase one unit
     * of the base currency.
     */
    fun symbols(): Future<Vector<HitbtcSymbol>> {
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/api/2/public/symbol")
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        val type = object : TypeReference<Vector<HitbtcSymbol>>() {}
        return sendJson(request, type)
    }

    fun symbol(pair: ExchangePair): Future<HitbtcSymbol> {
        val uri = "/api/2/public/symbol/${pair.symbol()}"
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri)
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        val type = object : TypeReference<HitbtcSymbol>() {}
        return sendJson(request, type)
    }

    /**
     * Return ticker information
     */
    fun tickers(): Future<Vector<HitbtcTicker>> {
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/api/2/public/ticker")
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        val type = object : TypeReference<Vector<HitbtcTicker>>() {}
        return sendJson(request, type)
    }

    fun ticker(pair: ExchangePair): Future<HitbtcTicker> {
        val uri = "/api/2/public/ticker/${pair.symbol()}"
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri)
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        val type = object : TypeReference<HitbtcTicker>() {}
        return sendJson(request, type)
    }

    /**
     * Return trades information
     */
    fun trades(
        pair: ExchangePair,
        sort: HitbtcSort? = null,
        by: HitbtcBy? = null,
        from: Long? = null,
        till: Long? = null,
        limit: Long? = null,
        offset: Long? = null
    ): Future<Vector<HitbtcTrade>> {
        val params = Vector.of(
            Option.of(sort).map { s -> "sort=${s!!.value}" },
            Option.of(by).map { b -> "by=${b!!.value}" },
            Option.of(from).map { b -> "from=$b" },
            Option.of(till).map { b -> "till=$b" },
            Option.of(limit).map { b -> "limit=$b" },
            Option.of(offset).map { b -> "limit=$b" }
        ).filter { it.isDefined }.map { it.get() }.mkString("&")
        val uri = "/api/2/public/trades/${pair.symbol()}?$params"
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri)
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        val type = object : TypeReference<Vector<HitbtcTrade>>() {}
        return sendJson(request, type)
    }

    /**
     * An order book is an electronic list of buy and sell orders for a specific symbol, organized by price level.
     */
    fun orderBook(pair: ExchangePair, limit: Long? = null): Future<HitbtcOrderBook> {
        val params = Option.of(limit).map { b -> "limit=$b" }.getOrElse("")
        val uri = "/api/2/public/orderbook/${pair.symbol()}?$params"
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri)
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        val type = object : TypeReference<HitbtcOrderBook>() {}
        return sendJson(request, type)
    }

    /**
     * An candles used for OHLC a specific symbol.
     */
    fun candles(
        pair: ExchangePair,
        limit: Long? = null,
        period: HitbtcPeriod? = null
    ): Future<Vector<HitbtcCandle>> {
        val params = Vector.of(
            Option.of(limit).map { b -> "limit=$b" },
            Option.of(period).map { b -> "period=$b" }
        ).filter { it.isDefined }.map { it.get() }.mkString("&")
        val uri = "/api/2/public/candles/${pair.symbol()}?$params"
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri)
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        val type = object : TypeReference<Vector<HitbtcCandle>>() {}
        return sendJson(request, type)
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
                400 -> {
                    val message = json.deserialize(response.content(), object : TypeReference<HitbtcError>() {})
                    response.release()
                    logger.warn("bad request: {} {}", message.error.code, message.error.message)
                    throw HitbtcClientException(message.error.code, message.error.message)
                }
                in 400..499 -> {
                    val message = response.content().toString(charset)
                    response.release()
                    logger.warn(message)
                    throw HitbtcClientException(code, message)
                }
                504 -> {
                    val message = response.content().toString(charset)
                    response.release()
                    logger.warn(message)
                    throw HitbtcUnknownException(code, message)
                }
                in 500..599 -> {
                    val message = response.content().toString(charset)
                    response.release()
                    logger.warn(message)
                    throw HitbtcServerException(code, message)
                }
                else -> {
                    val message = response.content().toString(charset)
                    response.release()
                    logger.warn(message)
                    throw HitbtcUnknownException(code, message)
                }
            }
        }
    }

    private fun <T> sendJson(request: HttpRequest, type: TypeReference<T>): Future<T> {
        return send(request).map { response: FullHttpResponse ->
            logger.debug("deserialize {}", response.content().toString(charset))
            val mapped: T = json.deserialize(response.content(), type)
            logger.debug("response: {}", mapped)
            response.release()
            mapped
        }
    }
}

class HitbtcSort private constructor(val value: String) {
    companion object {
        val asc = HitbtcSort("ASC")
        val desc = HitbtcSort("DESC")
    }
}

class HitbtcBy private constructor(val value: String) {
    companion object {
        val id = HitbtcBy("id")
        val time = HitbtcBy("timestamp")
    }
}

class HitbtcPeriod private constructor(val value: String) {
    companion object {
        val m1 = HitbtcPeriod("M1")
        val m3 = HitbtcPeriod("M3")
        val m5 = HitbtcPeriod("M5")
        val m15 = HitbtcPeriod("M15")
        val m30 = HitbtcPeriod("M30")
        val h1 = HitbtcPeriod("H1")
        val h4 = HitbtcPeriod("H4")
        val d1 = HitbtcPeriod("D1")
        val d7 = HitbtcPeriod("D7")
        val M = HitbtcPeriod("1M")
    }
}

data class HitbtcErrorInfo(val code: Int, val message: String, val description: String)
data class HitbtcError(val error: HitbtcErrorInfo)

data class HitbtcCurrency(
    val id: String,
    val fullName: String,
    val crypto: Boolean,
    val payinEnabled: Boolean,
    val payinPaymentId: Boolean,
    val payinConfirmations: Long,
    val payoutEnabled: Boolean,
    val payoutIsPaymentId: Boolean,
    val transferEnabled: Boolean
)

data class HitbtcSymbol(
    val id: String,
    val baseCurrency: String,
    val quoteCurrency: String,
    val quantityIncrement: BigDecimal,
    val tickSize: BigDecimal,
    val takeLiquidityRate: BigDecimal,
    val provideLiquidityRate: BigDecimal,
    val feeCurrency: String
)

data class HitbtcTicker(
    val ask: BigDecimal,
    val bid: BigDecimal?,
    val last: BigDecimal,
    val open: BigDecimal?,
    val low: BigDecimal,
    val high: BigDecimal,
    val volume: BigDecimal,
    val volumeQuote: BigDecimal,
    val timestamp: DateTime,
    val symbol: String
)

data class HitbtcTrade(
    val id: Long,
    val price: BigDecimal,
    val quantity: BigDecimal,
    val side: String,
    val timestamp: DateTime
)

data class HitbtcOrderBookAsk(val price: BigDecimal, val size: BigDecimal)
data class HitbtcOrderBookBid(val price: BigDecimal, val size: BigDecimal)
data class HitbtcOrderBook(
    val ask: Vector<HitbtcOrderBookAsk>,
    val bid: Vector<HitbtcOrderBookBid>
)

data class HitbtcCandle(
    val timestamp: DateTime,
    val open: BigDecimal,
    val close: BigDecimal,
    val min: BigDecimal,
    val max: BigDecimal,
    val volume: BigDecimal,
    val volumeQuote: BigDecimal
)