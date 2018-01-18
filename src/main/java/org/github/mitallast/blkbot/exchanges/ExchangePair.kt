package org.github.mitallast.blkbot.exchanges

import io.vavr.collection.Map
import io.vavr.concurrent.Future

data class ExchangePair(
        val base: String,
        val quote: String
) {
    fun symbol(): String = "$base$quote"
    fun symbol(c: Char): String = "$base$c$quote"

    override fun toString(): String = symbol('-')
}

interface ExchangeTradeProvider {
    fun name(): String
    fun trades(): Future<Map<ExchangePair, Double>>
}