package org.github.mitallast.blkbot.exchanges.bittrex

import io.vavr.Tuple
import io.vavr.collection.Vector
import io.vavr.concurrent.Future
import org.github.mitallast.blkbot.exchanges.ExchangePair
import org.github.mitallast.blkbot.exchanges.ExchangeTrade
import org.github.mitallast.blkbot.exchanges.ExchangeTradeProvider
import javax.inject.Inject

class BittrexTradeProvider @Inject constructor(val bittrex: BittrexClient) : ExchangeTradeProvider {
    override fun name(): String = "Bittrex"

    override fun trades(): Future<Vector<ExchangeTrade>> {
        val info = bittrex.markets()
        val prices = bittrex.marketSummaries()
        return info.flatMap { prices }.map {
            val symbols = info.get().toMap { s -> Tuple.of(s.marketName, s) }
            prices.get().map { price ->
                val symbol = symbols.apply(price.marketName)
                val pair = ExchangePair(symbol.baseCurrency, symbol.marketCurrency)
                ExchangeTrade(pair, price.last)
            }
        }
    }
}