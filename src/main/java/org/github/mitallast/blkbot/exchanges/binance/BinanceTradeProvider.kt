package org.github.mitallast.blkbot.exchanges.binance

import com.typesafe.config.Config
import io.vavr.Tuple
import io.vavr.collection.Map
import io.vavr.collection.Vector
import io.vavr.concurrent.Future
import org.github.mitallast.blkbot.exchanges.ExchangePair
import org.github.mitallast.blkbot.exchanges.ExchangeTrade
import org.github.mitallast.blkbot.exchanges.ExchangeTradeProvider
import java.math.BigDecimal
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject

class BinanceTradeProvider @Inject constructor(
    config: Config,
    private val binance: BinanceClient
) : ExchangeTradeProvider {
    private var lock = ReentrantLock()
    private var time: Long = 0
    private val cache: Long = config.getDuration("binance.cache").toMillis()
    private var tickers: Future<Vector<BinancePriceChangeStatistics>>? = null

    override fun name(): String = "Binance"

    override fun trades(): Future<Map<ExchangePair, ExchangeTrade>> {
        val info = binance.exchangeInfo()
        val prices = binance.tickerPrices()
        val tickers24h = tickers24h()
        return info.flatMap { prices }.flatMap { tickers24h }.map {
            val symbols = info.get().symbols.toMap { s -> Tuple.of(s.symbol, s) }
            val tickers = tickers24h.get().toMap { t -> Tuple.of(t.symbol, t) }
            prices.get().toMap { price ->
                val symbol = symbols.apply(price.symbol)
                val ticker = tickers.apply(price.symbol)
                val pair = ExchangePair(symbol.baseAsset, symbol.quoteAsset)
                val trade = ExchangeTrade(
                    pair = pair,
                    price = price.price,
                    volume = ticker.volume,
                    bid = ticker.bidPrice,
                    ask = ticker.askPrice
                )
                Tuple.of(pair, trade)
            }.filterValues { it.volume > BigDecimal.ONE }
        }
    }

    private fun tickers24h(): Future<Vector<BinancePriceChangeStatistics>> {
        lock.lock()
        try {
            val now = System.currentTimeMillis()
            if (time + cache < now) {
                time = now
                tickers = binance.tickers24hr()
            }
            return tickers!!
        } finally {
            lock.unlock()
        }
    }
}