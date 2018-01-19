package org.github.mitallast.blkbot.exchanges.cryptopia

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
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
import java.math.BigDecimal
import java.net.URI
import java.nio.charset.Charset
import javax.inject.Inject

open class CryptopiaException(message: String) : RuntimeException(message)
class CryptopiaClientException(val code: Int, message: String) : CryptopiaException(message)
class CryptopiaServerException(val code: Int, message: String) : CryptopiaException(message)
class CryptopiaUnknownException(val code: Int, message: String) : CryptopiaException(message)

/**
 * See https://www.cryptopia.co.nz/Forum/Thread/255
 */
class CryptopiaClient @Inject constructor(
    private val config: Config,
    private val json: JsonService,
    private val http: HttpClient
) {
    private val logger = LogManager.getLogger()

    private val charset = Charset.forName("UTF-8")
    private val endpoint = URI(config.getString("cryptopia.endpoint"))
    private val host = endpoint.host
    private val connect = http.connect(endpoint)

    /**
     * Returns all currency data
     */
    fun currencies(): Future<Vector<CryptopiaCurrency>> {
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/api/GetCurrencies")
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        val ref = object : TypeReference<CryptopiaResponse<Vector<CryptopiaCurrency>>>() {}
        return sendJson(request, ref)
    }

    /**
     * Returns all trade pair data.
     */
    fun tradePairs(): Future<Vector<CryptopiaTradePair>> {
        val url = "/api/GetTradePairs"
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, url)
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        val ref = object : TypeReference<CryptopiaResponse<Vector<CryptopiaTradePair>>>() {}
        return sendJson(request, ref)
    }

    /**
     * Returns all market data.
     */
    fun markets(
        symbol: Option<String> = Option.none(),
        hours: Option<Int> = Option.none()
    ): Future<Vector<CryptopiaMarket>> {
        val url = "/api/GetMarkets" +
            symbol.map { s -> "/" + s }.getOrElse("") +
            hours.map { h -> "/" + h }.getOrElse("")
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, url)
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        val ref = object : TypeReference<CryptopiaResponse<Vector<CryptopiaMarket>>>() {}
        return sendJson(request, ref)
    }

    /**
     * Returns market data for the specified trade pair
     */
    fun market(
        pair: ExchangePair,
        hours: Option<Int> = Option.none()
    ): Future<CryptopiaMarket> {
        val url = "/api/GetMarket/" +
            pair.symbol('_') +
            hours.map { h -> "/" + h }.getOrElse("")
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, url)
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        val ref = object : TypeReference<CryptopiaResponse<CryptopiaMarket>>() {}
        return sendJson(request, ref)
    }

    /**
     * Returns the market history data for the specified trade pair.
     */
    fun marketHistory(
        pair: Option<ExchangePair> = Option.none(),
        hours: Option<Int> = Option.none()
    ): Future<Vector<CryptopiaMarketHistory>> {
        val url = "/api/GetMarketHistory" +
            pair.map { p -> "/" + p.symbol('_') }.getOrElse("") +
            hours.map { h -> "/" + h }.getOrElse("")
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, url)
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        val ref = object : TypeReference<CryptopiaResponse<Vector<CryptopiaMarketHistory>>>() {}
        return sendJson(request, ref)
    }

    /**
     * Returns the open buy and sell orders for the specified trade pair.
     */
    fun marketOrders(
        pair: ExchangePair,
        hours: Option<Int> = Option.none()
    ): Future<CryptopiaOrderBook> {
        val url = "/api/GetMarketOrders/" +
            pair.symbol('_') +
            hours.map { h -> "/" + h }.getOrElse("")
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, url)
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        val ref = object : TypeReference<CryptopiaResponse<CryptopiaOrderBook>>() {}
        return sendJson(request, ref)
    }

    /**
     * Returns the open buy and sell orders for the specified markets.
     */
    fun marketOrderGroups(
        pairs: Vector<ExchangePair>,
        orderCount: Option<Int> = Option.none()
    ): Future<Vector<CryptopiaOrderGroup>> {
        val url = "/api/GetMarketOrderGroups/" +
            pairs.map { p -> p.symbol('_') }.mkString("-") + '/' +
            orderCount.map { h -> "/" + h }.getOrElse("")
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, url)
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        val ref = object : TypeReference<CryptopiaResponse<Vector<CryptopiaOrderGroup>>>() {}
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
                    throw CryptopiaClientException(code, message)
                }
                504 -> {
                    val message = response.content().toString(charset)
                    response.release()
                    logger.warn(message)
                    throw CryptopiaUnknownException(code, message)
                }
                in 500..599 -> {
                    val message = response.content().toString(charset)
                    response.release()
                    logger.warn(message)
                    throw CryptopiaServerException(code, message)
                }
                else -> {
                    val message = response.content().toString(charset)
                    response.release()
                    logger.warn(message)
                    throw CryptopiaUnknownException(code, message)
                }
            }
        }
    }

    private fun <T> sendJson(request: HttpRequest, type: TypeReference<CryptopiaResponse<T>>): Future<T> {
        return send(request).map { response: FullHttpResponse ->
            logger.debug("deserialize {}", response.content().toString(charset))
            val mapped: CryptopiaResponse<T> = json.deserialize(response.content(), type)
            logger.debug("response: {}", mapped)
            response.release()
            when {
                mapped.error != null -> throw CryptopiaException(mapped.error)
                mapped.success -> mapped.result!!
                else -> throw CryptopiaException("unexpected exception")
            }
        }
    }
}

data class CryptopiaResponse<out T>(
    @JsonProperty("Success") val success: Boolean,
    @JsonProperty("Message") val message: String?,
    @JsonProperty("Error") val error: String?,
    @JsonProperty("Data") val result: T?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CryptopiaCurrency(
    @JsonProperty("Id") val id: Long,
    @JsonProperty("Name") val name: String,
    @JsonProperty("Symbol") val symbol: String,
    @JsonProperty("Algorithm") val algorithm: String,
    @JsonProperty("WothdrawFee") val withdrawFee: BigDecimal,
    @JsonProperty("MinWithdraw") val minWithdraw: BigDecimal,
    @JsonProperty("MinBaseTrade") val minBaseTrade: BigDecimal,
    @JsonProperty("IsTipEnabled") val isTipEnabled: Boolean,
    @JsonProperty("MinTip") val minTip: BigDecimal,
    @JsonProperty("DepositConfirmations") val depositConfirmations: Int,
    @JsonProperty("Status") val status: String,
    @JsonProperty("StatusMessage") val statusMessage: String?,
    @JsonProperty("ListingStatus") val listingStatus: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CryptopiaTradePair(
    @JsonProperty("Id") val id: Long,
    @JsonProperty("Label") val label: String,
    @JsonProperty("Currency") val currency: String?,
    @JsonProperty("Symbol") val symbol: String,
    @JsonProperty("BaseCurrency") val baseCurrency: String?,
    @JsonProperty("BaseSymbol") val baseSymbol: String,
    @JsonProperty("Status") val status: String,
    @JsonProperty("StatusMessage") val statusMessage: String?,
    @JsonProperty("TradeFee") val tradeFee: BigDecimal,
    @JsonProperty("MinimumTrade") val minTrade: BigDecimal,
    @JsonProperty("MaximumTrade") val maxTrade: BigDecimal,
    @JsonProperty("MinimumBaseTrade") val minBaseTrade: BigDecimal,
    @JsonProperty("MaximumBaseTrade") val maxBaseTrade: BigDecimal,
    @JsonProperty("MinimumPrice") val minPrice: BigDecimal,
    @JsonProperty("MaximumPrice") val maxPrice: BigDecimal
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CryptopiaMarket(
    @JsonProperty("TradePairId") val tradePairId: Long,
    @JsonProperty("Label") val label: String,
    @JsonProperty("AskPrice") val ask: BigDecimal,
    @JsonProperty("BidPrice") val bid: BigDecimal,
    @JsonProperty("Low") val low: BigDecimal,
    @JsonProperty("High") val high: BigDecimal,
    @JsonProperty("Volume") val volume: BigDecimal,
    @JsonProperty("LastPrice") val last: BigDecimal,
    @JsonProperty("BuyVolume") val buyVolume: BigDecimal,
    @JsonProperty("SellVolume") val sellVolume: BigDecimal,
    @JsonProperty("Change") val change: BigDecimal,
    @JsonProperty("Open") val open: BigDecimal,
    @JsonProperty("Close") val close: BigDecimal,
    @JsonProperty("BaseVolume") val baseVolume: BigDecimal,
    @JsonProperty("BaseBuyVolume") val baseBuyVolume: BigDecimal?,
    @JsonProperty("BaseSellVolume") val baseSellVolume: BigDecimal?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CryptopiaMarketHistory(
    @JsonProperty("TradePairId") val id: Long,
    @JsonProperty("Label") val label: String,
    @JsonProperty("Type") val type: String,
    @JsonProperty("Price") val price: BigDecimal,
    @JsonProperty("Amount") val amount: BigDecimal,
    @JsonProperty("Total") val total: BigDecimal,
    @JsonProperty("Timestamp") val timestamp: Long
)

data class CryptopiaOrderBookBuy(
    @JsonProperty("TradePairId") val id: Long,
    @JsonProperty("Label") val label: String,
    @JsonProperty("Price") val price: BigDecimal,
    @JsonProperty("Volume") val volume: BigDecimal,
    @JsonProperty("Total") val total: BigDecimal
)

data class CryptopiaOrderBookSell(
    @JsonProperty("TradePairId") val id: Long,
    @JsonProperty("Label") val label: String,
    @JsonProperty("Price") val price: BigDecimal,
    @JsonProperty("Volume") val volume: BigDecimal,
    @JsonProperty("Total") val total: BigDecimal
)

data class CryptopiaOrderBook(
    @JsonProperty("Buy") val buy: Vector<CryptopiaOrderBookBuy>,
    @JsonProperty("Sell") val sell: Vector<CryptopiaOrderBookSell>
)

data class CryptopiaOrderGroup(
    @JsonProperty("TradePairId") val id: Long,
    @JsonProperty("Market") val symbol: String,
    @JsonProperty("Buy") val buy: Vector<CryptopiaOrderBookBuy>,
    @JsonProperty("Sell") val sell: Vector<CryptopiaOrderBookSell>
)
