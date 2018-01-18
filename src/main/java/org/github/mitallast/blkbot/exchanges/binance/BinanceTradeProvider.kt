package org.github.mitallast.blkbot.exchanges.binance

import io.vavr.Tuple
import io.vavr.collection.Vector
import io.vavr.concurrent.Future
import org.github.mitallast.blkbot.exchanges.ExchangePair
import org.github.mitallast.blkbot.exchanges.ExchangeTrade
import org.github.mitallast.blkbot.exchanges.ExchangeTradeProvider
import javax.inject.Inject

class BinanceTradeProvider @Inject constructor(private val binance: BinanceClient) : ExchangeTradeProvider {
    override fun name(): String = "Binance"

    override fun trades(): Future<Vector<ExchangeTrade>> {
        val info = binance.exchangeInfo()
        val prices = binance.tickerPrices()
        return info.flatMap { prices }.map {
            val symbols = info.get().symbols.toMap { s -> Tuple.of(s.symbol, s) }
            prices.get().map { price ->
                val symbol = symbols.apply(price.symbol)
                val pair = ExchangePair(symbol.baseAsset, symbol.quoteAsset)
                ExchangeTrade(pair, price.price)
            }
        }
    }
}