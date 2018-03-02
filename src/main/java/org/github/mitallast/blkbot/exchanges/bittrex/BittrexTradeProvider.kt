package org.github.mitallast.blkbot.exchanges.bittrex

import io.vavr.Tuple
import io.vavr.collection.Map
import io.vavr.concurrent.Future
import org.github.mitallast.blkbot.exchanges.ExchangePair
import org.github.mitallast.blkbot.exchanges.ExchangeTrade
import org.github.mitallast.blkbot.exchanges.ExchangeTradeProvider
import java.math.BigDecimal
import javax.inject.Inject

class BittrexTradeProvider @Inject constructor(val bittrex: BittrexClient) : ExchangeTradeProvider {
    override fun name(): String = "Bittrex"

    override fun trades(): Future<Map<ExchangePair, ExchangeTrade>> {
        val info = bittrex.markets()
        val prices = bittrex.marketSummaries()
        return info.flatMap { prices }.map {
            val symbols = info.get().toMap { s -> Tuple.of(s.marketName, s) }
            prices.get()
                .toMap { price ->
                    val symbol = symbols.apply(price.marketName)
                    val pair = ExchangePair(symbol.baseCurrency, symbol.marketCurrency)
                    val trade = ExchangeTrade(
                        pair = pair,
                        price = price.last,
                        volumeBase = price.baseVolume,
                        volumeQuote = price.volume,
                        bid = price.bid,
                        ask = price.ask
                    )
                    Tuple.of(pair, trade)
                }
        }
    }
}