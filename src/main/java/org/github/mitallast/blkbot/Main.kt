package org.github.mitallast.blkbot

import com.typesafe.config.ConfigFactory
import io.vavr.collection.Vector
import io.vavr.control.Option
import org.github.mitallast.blkbot.common.http.HttpClient
import org.github.mitallast.blkbot.common.netty.NettyProvider
import org.github.mitallast.blkbot.exchanges.ExchangePair
import org.github.mitallast.blkbot.exchanges.binance.*
import org.github.mitallast.blkbot.exchanges.bittrex.BittrexClient
import org.github.mitallast.blkbot.exchanges.bittrex.BittrexOrderType
import org.github.mitallast.blkbot.exchanges.cryptopia.CryptopiaClient
import java.net.URI
import java.util.concurrent.CountDownLatch

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        val config = ConfigFactory.load()
        val bot = Bot(config)
        bot.start()

        val pair = ExchangePair("DOT", "BTC")

//        val limit = BinanceLimit.limit5
//        val interval = BinanceInterval.int5m
//        val binance = bot.injector().getInstance(BinanceClient::class.java)
//        binance.ping().await().onComplete{ r -> println(r) }
//        binance.time().await().onComplete{ r -> println(r) }
//        binance.depth(pair, limit).await().onComplete{ r -> println(r) }
//        binance.aggTrades(pair).await().onComplete{ r -> println(r) }
//        binance.klines(pair, interval).await().onComplete{ r -> println(r) }
//        binance.ticker24hr(pair).await().onComplete{ r -> println(r) }
//        binance.allPrices().await().onComplete{ r -> println(r) }
//        binance.allBookTickers().await().onComplete{ r -> println(r) }
//        val listener = object : BinanceListener<EventTrades> {
//            override fun handle(event: EventTrades) {}
//            override fun close() {}
//        }
//        binance.tradesDataStream(pair, listener)

//        val bittrex = bot.injector().getInstance(BittrexClient::class.java)
//        bittrex.markets().await().onComplete { r -> println(r) }
//        bittrex.currencies().await().onComplete { r -> println(r) }
//        bittrex.ticker(pair).await().onComplete { r -> println(r) }
//        bittrex.marketSummaries().await().onComplete { r -> println(r) }
//        bittrex.marketSummary(pair).await().onComplete { r -> println(r) }
//        bittrex.orderBook(pair, BittrexOrderType.both).await().onComplete { r -> println(r) }
//        bittrex.marketHistory(pair).await().onComplete { r -> println(r) }

//        val cryptopia = bot.injector().getInstance(CryptopiaClient::class.java)
//        cryptopia.currencies().await().onComplete { r -> println(r) }
//        cryptopia.tradePairs().await().onComplete { r -> println(r) }
//        cryptopia.markets(Option.some("BTC")).await().onComplete { r -> println(r) }
//        cryptopia.market(pair).await().onComplete { r -> println(r) }
//        cryptopia.marketHistory(Option.some(pair)).await().onComplete { r -> println(r) }
//        cryptopia.marketOrders(pair).await().onComplete { r -> println(r) }
//        cryptopia.marketOrderGroups(Vector.of(pair)).await().onComplete { r -> println(r) }

        val countDownLatch = CountDownLatch(1)
        Runtime.getRuntime().addShutdownHook(Thread {
            bot.close()
            countDownLatch.countDown()
        })
        countDownLatch.await()
    }
}