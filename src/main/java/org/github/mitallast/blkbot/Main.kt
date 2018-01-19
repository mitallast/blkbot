package org.github.mitallast.blkbot

import com.typesafe.config.ConfigFactory
import org.github.mitallast.blkbot.exchanges.ExchangeArbitrationHistory
import org.github.mitallast.blkbot.exchanges.ExchangeArbitrator
import org.github.mitallast.blkbot.exchanges.ExchangePair
import java.util.concurrent.CountDownLatch

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        val config = ConfigFactory.load()
        val bot = Bot(config)
        bot.start()

//        val pair = ExchangePair("BTC", "LTC")
        val pair = ExchangePair("LTC", "ETH")

//        val limit = BinanceLimit.limit5
//        val interval = BinanceInterval.int5m
//        val binance = bot.injector().getInstance(BinanceClient::class.java)
//        binance.ping().await().onComplete{ r -> println(r) }
//        binance.time().await().onComplete{ r -> println(r) }
//        while (!Thread.interrupted()) {
//            binance.exchangeInfo().await().onComplete { r -> println(r) }
//            Thread.sleep(10000)
//        }
//        binance.depth(pair, limit).await().onComplete{ r -> println(r) }
//        binance.trades(pair).await().onComplete{ r -> println(r) }
//        binance.historicalTrades(pair).await().onComplete{ r -> println(r) }
//        binance.aggTrades(pair).await().onComplete{ r -> println(r) }
//        binance.klines(pair, interval).await().onComplete{ r -> println(r) }
//        binance.ticker24hr(pair).await().onComplete{ r -> println(r) }
//        binance.tickerPrices().await().onComplete{ r -> println(r) }
//        binance.tickerPrice(pair).await().onComplete{ r -> println(r) }
//        val listener = object : BinanceListener<EventTrades> {
//            override fun handle(event: EventTrades) {}
//            override fun close() {}
//        }
//        binance.tradesDataStream(pair, listener)

//        val bittrex = bot.injector().getInstance(BittrexClient::class.java)
//        bittrex.markets().await().onComplete { r -> println(r) }
//        bittrex.currencies().await().onComplete { r -> println(r) }
//        bittrex.ticker(pair).await().onComplete { r -> println(r.last()) }
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

//        val hitbtc = bot.injector().getInstance(HitbtcClient::class.java)
//        hitbtc.currencies().await().onComplete { r -> println(r) }
//        hitbtc.currency("1ST").await().onComplete { r -> println(r) }
//        hitbtc.symbols().await().onComplete { r -> println(r) }
//        hitbtc.symbol(pair).await().onComplete { r -> println(r) }
//        hitbtc.tickers().await().onComplete { r -> println(r) }
//        hitbtc.ticker(pair).await().onComplete { r -> println(r) }
//        hitbtc.trades(pair).await().onComplete { r -> println(r) }
//        hitbtc.orderBook(pair).await().onComplete { r -> println(r) }
//        hitbtc.candles(pair).await().onComplete { r -> println(r) }

//        val history = bot.injector().getInstance(ExchangeArbitrationHistory::class.java)
//        val arbitrator = bot.injector().getInstance(ExchangeArbitrator::class.java)
//        arbitrator.compute(1000).await().onComplete { top ->
//            history.save(System.currentTimeMillis(), top.get())
//            println("top pairs:")
//            top.get().forEach { p -> println("${p.difference}% $p") }
//        }

        val countDownLatch = CountDownLatch(1)
        Runtime.getRuntime().addShutdownHook(Thread {
            bot.close()
            countDownLatch.countDown()
        })
        countDownLatch.await()
    }
}