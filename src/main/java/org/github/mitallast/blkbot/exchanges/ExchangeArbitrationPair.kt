package org.github.mitallast.blkbot.exchanges

import java.math.BigDecimal

data class ExchangeArbitrationPair(
    val pair: ExchangePair,
    val leftExchange: String,
    val rightExchange: String,
    val leftPrice: BigDecimal,
    val rightPrice: BigDecimal,
    val leftVolume: BigDecimal,
    val rightVolume: BigDecimal,
    val leftAsk: BigDecimal,
    val rightAsk: BigDecimal,
    val leftBid: BigDecimal,
    val rightBid: BigDecimal
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