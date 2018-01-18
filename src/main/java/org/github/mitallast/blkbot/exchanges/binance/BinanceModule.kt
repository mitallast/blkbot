package org.github.mitallast.blkbot.exchanges.binance

import com.google.inject.AbstractModule

class BinanceModule() : AbstractModule() {
    override fun configure() {
        bind(BinanceClient::class.java).asEagerSingleton()
        bind(BinanceTradeProvider::class.java).asEagerSingleton()
    }
}