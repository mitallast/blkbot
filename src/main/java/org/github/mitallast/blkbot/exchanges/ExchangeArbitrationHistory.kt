package org.github.mitallast.blkbot.exchanges

import io.vavr.collection.Vector
import org.apache.logging.log4j.LogManager
import org.github.mitallast.blkbot.persistence.PersistenceService
import org.github.mitallast.blkbot.persistence.schema.Tables.EXCHANGE_ARBITRATION_HISTORY
import javax.inject.Inject

class ExchangeArbitrationHistory @Inject constructor(
    private val db: PersistenceService
) {
    private val logger = LogManager.getLogger()

    fun save(timestamp: Long, pairs: Vector<ExchangeArbitrationPair>) {
        db.context().use { ctx ->
            val h = EXCHANGE_ARBITRATION_HISTORY
            var insert = ctx.insertInto(h, h.TIMESTAMP, h.BASE, h.QUOTE, h.LEFT_EXCHANGE, h.RIGHT_EXCHANGE, h.LEFT_PRICE, h.RIGHT_PRICE)
            pairs.forEach { pair ->
                insert = insert.values(
                    db.timestamp(timestamp),
                    pair.pair.base,
                    pair.pair.quote,
                    pair.leftExchange,
                    pair.rightExchange,
                    pair.leftPrice,
                    pair.rightPrice
                )
            }
            insert.execute()
        }
    }
}