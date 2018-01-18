package org.github.mitallast.blkbot.exchanges.binance

import io.vavr.Tuple
import io.vavr.collection.Map
import io.vavr.concurrent.Future
import org.github.mitallast.blkbot.exchanges.ExchangePair
import org.github.mitallast.blkbot.exchanges.ExchangeTradeProvider
import javax.inject.Inject

class BinanceTradeProvider @Inject constructor(val binance: BinanceClient) : ExchangeTradeProvider {
    override fun name(): String = "Binance"

    override fun trades(): Future<Map<ExchangePair, Double>> {
        val info = binance.exchangeInfo()
        val prices = binance.tickerPrices()
        return info.flatMap { prices }.map {
            val symbols = info.get().symbols.toMap { s -> Tuple.of(s.symbol, s) }
            prices.get().toMap { price ->
                val symbol = symbols.apply(price.symbol)
                val pair = ExchangePair(symbol.baseAsset, symbol.quoteAsset)
                Tuple.of(pair, price.price)
            }
        }
    }
}