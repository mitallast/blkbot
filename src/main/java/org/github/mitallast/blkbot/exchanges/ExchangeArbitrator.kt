package org.github.mitallast.blkbot.exchanges

import io.vavr.Tuple
import io.vavr.Tuple2
import io.vavr.collection.Map
import io.vavr.concurrent.Future
import io.vavr.collection.Vector
import org.apache.logging.log4j.LogManager
import javax.inject.Inject

data class ExchangeArbitrationPair(
        val pair: ExchangePair,
        val leftExchange: String,
        val leftPrice: Double,
        val rightExchange: String,
        val rightPrice: Double
) {
    val difference: Double = if (leftPrice > rightPrice) {
        100.0 - (rightPrice / leftPrice * 100)
    } else {
        100.0 - (leftPrice / rightPrice * 100)
    }
}

class ExchangeArbitrator @Inject constructor(exchanges: Set<@JvmSuppressWildcards ExchangeTradeProvider>) {
    private val logger = LogManager.getLogger()
    private val exchanges = Vector.ofAll(exchanges)

    fun compute(topN: Int = 10): Future<Vector<ExchangeArbitrationPair>> {
        logger.info("fetch info for ${exchanges.map { it.name() }}")
        val tasks = exchanges.map { exchange ->
            exchange.trades()
                    .map { Tuple.of(exchange.name(), it) }
        }
        return Future.sequence(tasks).map { info ->
            collect(info.toVector(), Vector.empty())
                    .sortBy { it.difference }
                    .reverse()
                    .take(topN)
        }
    }

    private fun collect(
            exchanges: Vector<Tuple2<String, Map<ExchangePair, Double>>>,
            acc: Vector<ExchangeArbitrationPair>
    ): Vector<ExchangeArbitrationPair> {
        return if (exchanges.size() <= 1) {
            logger.info("collect done: ${acc.size()}")
            acc
        } else {
            val head = exchanges.head()
            val leftExchange = head._1
            val tail = exchanges.tail()
            val rights = tail.toVector()
            logger.info("collect $leftExchange")
            val collected = head._2.toVector().flatMap { leftTrade ->
                val pair = leftTrade._1
                val leftPrice = leftTrade._2
                rights.flatMap { right ->
                    val rightExchange = right._1
                    right._2.get(pair).map { rightPrice ->
                        ExchangeArbitrationPair(pair, leftExchange, leftPrice, rightExchange, rightPrice)
                    }
                }
            }
            collect(tail, collected.appendAll(acc))
        }
    }

}