package org.github.mitallast.blkbot

import com.typesafe.config.ConfigFactory
import org.github.mitallast.blkbot.common.http.HttpClient
import org.github.mitallast.blkbot.common.netty.NettyProvider
import org.github.mitallast.blkbot.exchanges.ExchangePair
import org.github.mitallast.blkbot.exchanges.binance.*
import java.net.URI
import java.util.concurrent.CountDownLatch

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        val config = ConfigFactory.load()
        val bot = Bot(config)
        bot.start()

        val pair = ExchangePair("ETH", "BTC")
        val limit = BinanceLimit.limit5
        val interval = BinanceInterval.int5m

        val binance = bot.injector().getInstance(BinanceClient::class.java)
        binance.ping().await().onComplete{ r -> println(r) }
//        binance.time().await().onComplete{ r -> println(r) }
//        binance.depth(pair, limit).await().onComplete{ r -> println(r) }
//        binance.aggTrades(pair).await().onComplete{ r -> println(r) }
//        binance.klines(pair, interval).await().onComplete{ r -> println(r) }
//        binance.ticker24hr(pair).await().onComplete{ r -> println(r) }
//        binance.allPrices().await().onComplete{ r -> println(r) }
//        binance.allBookTickers().await().onComplete{ r -> println(r) }

        val listener = object : BinanceListener<EventTrades> {
            override fun handle(event: EventTrades) {}
            override fun close() {}
        }
        binance.tradesDataStream(pair, listener)

        val countDownLatch = CountDownLatch(1)
        Runtime.getRuntime().addShutdownHook(Thread {
            bot.close()
            countDownLatch.countDown()
        })
        countDownLatch.await()
    }
}