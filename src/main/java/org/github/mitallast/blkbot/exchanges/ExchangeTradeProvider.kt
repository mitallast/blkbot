package org.github.mitallast.blkbot.exchanges

import io.vavr.collection.Map
import io.vavr.concurrent.Future
import java.math.BigDecimal

interface ExchangeTradeProvider {
    fun name(): String
    fun trades(): Future<Map<ExchangePair, BigDecimal>>
}