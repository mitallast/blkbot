package org.github.mitallast.blkbot.exchanges.cryptopia

import io.vavr.Tuple
import io.vavr.collection.Map
import io.vavr.concurrent.Future
import org.apache.logging.log4j.LogManager
import org.github.mitallast.blkbot.exchanges.ExchangePair
import org.github.mitallast.blkbot.exchanges.ExchangeTradeProvider
import java.math.BigDecimal
import javax.inject.Inject

class CryptopiaTradeProvider @Inject constructor(private val cryptopia: CryptopiaClient) : ExchangeTradeProvider {
    private val logger = LogManager.getLogger()

    override fun name(): String = "Cryptopia"

    override fun trades(): Future<Map<ExchangePair, BigDecimal>> {
        val info = cryptopia.tradePairs()
        val prices = cryptopia.markets()
        return info.flatMap { prices }.map {
            val symbols = info.get().toMap { s -> Tuple.of(s.label, s) }
            prices.get()
                .filter { it.volume > BigDecimal.ONE }
                .toMap { price ->
                    val symbol = symbols.apply(price.label)

                    logger.info("$price")

                    val pair = ExchangePair(symbol.baseSymbol, symbol.symbol)
                    Tuple.of(pair, price.last)
                }
        }
    }
}