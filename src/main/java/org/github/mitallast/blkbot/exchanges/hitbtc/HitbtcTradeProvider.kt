package org.github.mitallast.blkbot.exchanges.hitbtc

import io.vavr.Tuple
import io.vavr.collection.Map
import io.vavr.concurrent.Future
import org.github.mitallast.blkbot.exchanges.ExchangePair
import org.github.mitallast.blkbot.exchanges.ExchangeTrade
import org.github.mitallast.blkbot.exchanges.ExchangeTradeProvider
import java.math.BigDecimal
import javax.inject.Inject

class HitbtcTradeProvider @Inject constructor(private val hitbtc: HitbtcClient) : ExchangeTradeProvider {
    override fun name(): String = "Hitbtc"

    override fun trades(): Future<Map<ExchangePair, ExchangeTrade>> {
        val info = hitbtc.symbols()
        val prices = hitbtc.tickers()
        return info.flatMap { prices }.map {
            val symbols = info.get().toMap { s -> Tuple.of(s.id, s) }
            prices.get()
                .toMap { price ->
                    val symbol = symbols.apply(price.symbol)
                    val pair = ExchangePair(symbol.baseCurrency, symbol.quoteCurrency)
                    val trade = ExchangeTrade(
                        pair = pair,
                        price = price.last,
                        volumeBase = price.volume,
                        volumeQuote = price.volumeQuote,
                        bid = price.bid ?: BigDecimal.ZERO,
                        ask = price.ask
                    )
                    Tuple.of(pair, trade)
                }
        }
    }
}