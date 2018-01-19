package org.github.mitallast.blkbot.exchanges.bittrex

import io.vavr.Tuple
import io.vavr.collection.Map
import io.vavr.concurrent.Future
import org.github.mitallast.blkbot.exchanges.ExchangePair
import org.github.mitallast.blkbot.exchanges.ExchangeTradeProvider
import java.math.BigDecimal
import javax.inject.Inject

class BittrexTradeProvider @Inject constructor(val bittrex: BittrexClient) : ExchangeTradeProvider {
    override fun name(): String = "Bittrex"

    override fun trades(): Future<Map<ExchangePair, BigDecimal>> {
        val info = bittrex.markets()
        val prices = bittrex.marketSummaries()
        return info.flatMap { prices }.map {
            val symbols = info.get().toMap { s -> Tuple.of(s.marketName, s) }
            prices.get()
                .filter { it.volume > BigDecimal.ONE }
                .toMap { price ->
                    val symbol = symbols.apply(price.marketName)
                    val pair = ExchangePair(symbol.baseCurrency, symbol.marketCurrency)
                    Tuple.of(pair, price.last)
                }
        }
    }
}