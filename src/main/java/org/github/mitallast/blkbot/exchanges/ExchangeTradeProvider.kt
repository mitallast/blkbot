package org.github.mitallast.blkbot.exchanges

import io.vavr.collection.Map
import io.vavr.concurrent.Future
import java.math.BigDecimal

data class ExchangeTrade(
    val pair: ExchangePair,
    val price: BigDecimal,
    val volumeBase: BigDecimal,
    val volumeQuote: BigDecimal,
    val bid: BigDecimal,
    val ask: BigDecimal
)

interface ExchangeTradeProvider {
    fun name(): String
    fun trades(): Future<Map<ExchangePair, ExchangeTrade>>
}