package org.github.mitallast.blkbot.exchanges.bittrex

import com.google.inject.AbstractModule

class BittrexModule() : AbstractModule() {
    override fun configure() {
        bind(BittrexClient::class.java).asEagerSingleton()
        bind(BittrexTradeProvider::class.java).asEagerSingleton()
    }
}
