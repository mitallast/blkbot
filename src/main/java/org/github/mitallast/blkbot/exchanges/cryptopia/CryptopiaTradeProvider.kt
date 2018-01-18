package org.github.mitallast.blkbot.exchanges.cryptopia

import io.vavr.Tuple
import io.vavr.collection.Map
import io.vavr.concurrent.Future
import org.github.mitallast.blkbot.exchanges.ExchangePair
import org.github.mitallast.blkbot.exchanges.ExchangeTradeProvider
import javax.inject.Inject

class CryptopiaTradeProvider @Inject constructor(private val cryptopia: CryptopiaClient) : ExchangeTradeProvider {
    override fun name(): String = "Cryptopia"

    override fun trades(): Future<Map<ExchangePair, Double>> {
        val info = cryptopia.tradePairs()
        val prices = cryptopia.markets()
        return info.flatMap { prices }.map {
            val symbols = info.get().toMap { s -> Tuple.of(s.label, s) }
            prices.get().toMap { price ->
                val symbol = symbols.apply(price.label)
                val pair = ExchangePair(symbol.baseSymbol, symbol.symbol)
                Tuple.of(pair, price.last)
            }
        }
    }
}