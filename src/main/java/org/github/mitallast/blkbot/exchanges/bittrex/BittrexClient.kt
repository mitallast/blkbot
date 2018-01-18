package org.github.mitallast.blkbot.exchanges.bittrex

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.type.TypeReference
import com.typesafe.config.Config
import io.netty.handler.codec.http.*
import io.vavr.collection.Vector
import io.vavr.concurrent.Future
import org.apache.logging.log4j.LogManager
import org.github.mitallast.blkbot.common.http.HttpClient
import org.github.mitallast.blkbot.common.json.JsonService
import org.github.mitallast.blkbot.exchanges.ExchangePair
import org.joda.time.DateTime
import java.net.URI
import java.nio.charset.Charset
import javax.inject.Inject

open class BittrexException(message: String) : RuntimeException(message)
class BittrexClientException(val code: Int, message: String) : BittrexException(message)
class BittrexServerException(val code: Int, message: String) : BittrexException(message)
class BittrexUnknownException(val code: Int, message: String) : BittrexException(message)

/**
 * See https://bittrex.com/Home/Api
 */
class BittrexClient @Inject constructor(
        private val config: Config,
        private val json: JsonService,
        private val http: HttpClient
) {
    private val logger = LogManager.getLogger()

    private val charset = Charset.forName("UTF-8")
    private val endpoint = URI(config.getString("bittrex.endpoint"))
    private val host = endpoint.host
    private val connect = http.connect(endpoint)

    /**
     * Used to get the open and available trading markets at Bittrex along with other meta data.
     */
    fun markets(): Future<Vector<BittrexMarket>> {
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/api/v1.1/public/getmarkets")
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        val ref = object : TypeReference<BittrexResponse<Vector<BittrexMarket>>>() {}
        return sendJson(request, ref)
    }

    /**
     * Used to get all supported currencies at Bittrex along with other meta data
     */
    fun currencies(): Future<Vector<BittrexCurrency>> {
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/api/v1.1/public/getcurrencies")
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        val ref = object : TypeReference<BittrexResponse<Vector<BittrexCurrency>>>() {}
        return sendJson(request, ref)
    }

    /**
     * Used to get the current tick values for a market.
     */
    fun ticker(pair: ExchangePair): Future<BittrexTicker> {
        val url = "/api/v1.1/public/getticker?market=${pair.symbol('-')}"
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, url)
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        val ref = object : TypeReference<BittrexResponse<BittrexTicker>>() {}
        return sendJson(request, ref)
    }

    /**
     * Used to get the current tick values for a market.
     */
    fun marketSummaries(): Future<Vector<BittrexMarketSummary>> {
        val url = "/api/v1.1/public/getmarketsummaries"
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, url)
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        val ref = object : TypeReference<BittrexResponse<Vector<BittrexMarketSummary>>>() {}
        return sendJson(request, ref)
    }

    /**
     * Used to get the current tick values for a market.
     */
    fun marketSummary(pair: ExchangePair): Future<BittrexMarketSummary> {
        val url = "/api/v1.1/public/getmarketsummary?market=${pair.symbol('-')}"
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, url)
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        val ref = object : TypeReference<BittrexResponse<Vector<BittrexMarketSummary>>>() {}
        return sendJson(request, ref).map { r -> r.head() }
    }

    /**
     * Used to get retrieve the orderbook for a given market
     */
    fun orderBook(pair: ExchangePair, type: BittrexOrderType): Future<BittrexOrderBook> {
        val url = "/api/v1.1/public/getorderbook?market=${pair.symbol('-')}&type=${type.value}"
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, url)
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        val ref = object : TypeReference<BittrexResponse<BittrexOrderBook>>() {}
        return sendJson(request, ref)
    }

    /**
     * Used to retrieve the latest trades that have occured for a specific market
     */
    fun marketHistory(pair: ExchangePair): Future<Vector<BittrexMarketHistory>> {
        val url = "/api/v1.1/public/getmarkethistory?market=${pair.symbol('-')}"
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, url)
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        val ref = object : TypeReference<BittrexResponse<Vector<BittrexMarketHistory>>>() {}
        return sendJson(request, ref)
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
                in 400..499 -> {
                    val message = response.content().toString(charset)
                    response.release()
                    logger.warn(message)
                    throw BittrexClientException(code, message)
                }
                504 -> {
                    val message = response.content().toString(charset)
                    response.release()
                    logger.warn(message)
                    throw BittrexUnknownException(code, message)
                }
                in 500..599 -> {
                    val message = response.content().toString(charset)
                    response.release()
                    logger.warn(message)
                    throw BittrexServerException(code, message)
                }
                else -> {
                    val message = response.content().toString(charset)
                    response.release()
                    logger.warn(message)
                    throw BittrexUnknownException(code, message)
                }
            }
        }
    }

    private fun <T> sendJson(request: HttpRequest, type: TypeReference<BittrexResponse<T>>): Future<T> {
        return send(request).map { response: FullHttpResponse ->
            logger.info("deserialize {}", response.content().toString(charset))
            val mapped: BittrexResponse<T> = json.deserialize(response.content(), type)
            logger.info("response: {}", mapped)
            response.release()
            when {
                mapped.success -> mapped.result!!
                else -> throw BittrexException(mapped.message)
            }
        }
    }
}

data class BittrexResponse<out T>(
        val success: Boolean,
        val message: String,
        val result: T?
)

@JsonIgnoreProperties(ignoreUnknown=true)
data class BittrexMarket(
        @JsonProperty("MarketCurrency") val marketCurrency: String,
        @JsonProperty("BaseCurrency") val baseCurrency: String,
        @JsonProperty("MarketCurrencyLong") val marketCurrencyLong: String,
        @JsonProperty("BaseCurrencyLong") val baseCurrencyLong: String,
        @JsonProperty("MinTradeSize") val minTradeSize: Double,
        @JsonProperty("MarketName") val marketName: String,
        @JsonProperty("IsActive") val isActive: Boolean,
        @JsonProperty("Created") val created: DateTime
)

@JsonIgnoreProperties(ignoreUnknown=true)
data class BittrexCurrency(
        @JsonProperty("Currency") val currency: String,
        @JsonProperty("CurrencyLong") val currencyLong: String,
        @JsonProperty("MinConfirmation") val minConfirmation: Int,
        @JsonProperty("TxFee") val txFee: Double,
        @JsonProperty("IsActive") val isActive: Boolean,
        @JsonProperty("CoinType") val coinType: String,
        @JsonProperty("BaseAddress") val baseAddress: String?
)

@JsonIgnoreProperties(ignoreUnknown=true)
data class BittrexTicker(
        @JsonProperty("Bid") val bid: Double,
        @JsonProperty("Ask") val ask: Double,
        @JsonProperty("Last") val last: Double
)

@JsonIgnoreProperties(ignoreUnknown=true)
data class BittrexMarketSummary(
        @JsonProperty("MarketName") val marketName: String,
        @JsonProperty("High") val high: Double,
        @JsonProperty("Low") val low: Double,
        @JsonProperty("Volume") val volume: Double,
        @JsonProperty("Last") val last: Double,
        @JsonProperty("BaseVolume") val baseVolume: Double,
        @JsonProperty("TimeStamp") val timestamp: DateTime,
        @JsonProperty("Bid") val bid: Double,
        @JsonProperty("Ask") val ask: Double,
        @JsonProperty("OpenBuyOrders") val openBuyOrders: Long,
        @JsonProperty("OpenSellOrders") val openSellOrders: Long,
        @JsonProperty("PrevDay") val prevDay: Double,
        @JsonProperty("Created") val created: DateTime,
        @JsonProperty("DisplayMarketName") val displayMarketName: String?
)

class BittrexOrderType private constructor(val value: String) {
    companion object {
        val buy = BittrexOrderType("buy")
        val sell = BittrexOrderType("sell")
        val both = BittrexOrderType("both")
    }
}

data class BittrexOrderBookBuy(
        @JsonProperty("Quantity") val quantity: Double,
        @JsonProperty("Rate") val rate: Double)
data class BittrexOrderBookSell(
        @JsonProperty("Quantity") val quantity: Double,
        @JsonProperty("Rate") val rate: Double
)
data class BittrexOrderBook(
        @JsonProperty("buy") val buy: Vector<BittrexOrderBookBuy>,
        @JsonProperty("sell") val sell: Vector<BittrexOrderBookSell>
)

@JsonIgnoreProperties(ignoreUnknown=true)
data class BittrexMarketHistory(
        @JsonProperty("Id") val id: Long,
        @JsonProperty("TimeStamp") val timestamp: DateTime,
        @JsonProperty("Quantity") val quantity: Double,
        @JsonProperty("Price") val price: Double,
        @JsonProperty("Total") val total: Double,
        @JsonProperty("FillType") val fillType: String,
        @JsonProperty("OrderType") val orderType: String
)