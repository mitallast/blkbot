package org.github.mitallast.blkbot.exchanges

data class ExchangePair(
    val base: String,
    val quote: String
) {
    fun symbol(): String = "$base$quote"
    fun symbol(c: Char): String = "$base$c$quote"

    override fun toString(): String = symbol('-')
}