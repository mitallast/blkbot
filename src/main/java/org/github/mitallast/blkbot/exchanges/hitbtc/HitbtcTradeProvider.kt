package org.github.mitallast.blkbot.exchanges.hitbtc

import io.vavr.Tuple
import io.vavr.collection.Map
import io.vavr.concurrent.Future
import org.github.mitallast.blkbot.exchanges.ExchangePair
import org.github.mitallast.blkbot.exchanges.ExchangeTradeProvider
import java.math.BigDecimal
import javax.inject.Inject

class HitbtcTradeProvider @Inject constructor(private val hitbtc: HitbtcClient) : ExchangeTradeProvider {
    override fun name(): String = "Hitbtc"

    override fun trades(): Future<Map<ExchangePair, BigDecimal>> {
        val info = hitbtc.symbols()
        val prices = hitbtc.tickers()
        return info.flatMap { prices }.map {
            val symbols = info.get().toMap { s -> Tuple.of(s.id, s) }
            prices.get()
                .filter { it.volume > BigDecimal.ONE }
                .toMap { price ->
                    val symbol = symbols.apply(price.symbol)
                    val pair = ExchangePair(symbol.baseCurrency, symbol.quoteCurrency)
                    Tuple.of(pair, price.last)
                }
        }
    }
}