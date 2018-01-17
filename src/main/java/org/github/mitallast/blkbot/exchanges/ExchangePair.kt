package org.github.mitallast.blkbot.exchanges

data class ExchangePair(val from: String, val to: String) {
    fun symbol(): String = "$from$to"
    fun symbol(c: Char): String = "$from$c$to"
}