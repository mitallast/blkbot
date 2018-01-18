package org.github.mitallast.blkbot.exchanges.hitbtc

import com.google.inject.AbstractModule

class HitbtcModule() : AbstractModule() {
    override fun configure() {
        bind(HitbtcClient::class.java).asEagerSingleton()
        bind(HitbtcTradeProvider::class.java).asEagerSingleton()
    }
}
