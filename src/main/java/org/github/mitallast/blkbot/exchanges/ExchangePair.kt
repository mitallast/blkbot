package org.github.mitallast.blkbot.exchanges

import io.vavr.collection.Vector
import io.vavr.concurrent.Future

data class ExchangePair(
        val base: String,
        val quote: String
) {
    fun symbol(): String = "$base$quote"
    fun symbol(c: Char): String = "$base$c$quote"
}

data class ExchangeTrade(
        val pair: ExchangePair,
        val lastPrice: Double
)

interface ExchangeTradeProvider {
    fun name(): String
    fun trades(): Future<Vector<ExchangeTrade>>
}