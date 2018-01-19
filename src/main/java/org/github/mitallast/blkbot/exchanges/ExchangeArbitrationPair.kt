package org.github.mitallast.blkbot.exchanges

import java.math.BigDecimal

data class ExchangeArbitrationPair(
    val pair: ExchangePair,
    val leftExchange: String,
    val leftPrice: BigDecimal,
    val rightExchange: String,
    val rightPrice: BigDecimal
) {
    val difference: BigDecimal = if (leftPrice > rightPrice) {
        d100 - (rightPrice / leftPrice * d100)
    } else {
        d100 - (leftPrice / rightPrice * d100)
    }

    companion object {
        val d100 = BigDecimal(100)
    }
}