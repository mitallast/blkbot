package org.github.mitallast.blkbot.exchanges.cryptopia

import com.google.inject.AbstractModule

class CryptopiaModule() : AbstractModule() {
    override fun configure() {
        bind(CryptopiaClient::class.java).asEagerSingleton()
        bind(CryptopiaTradeProvider::class.java).asEagerSingleton()
    }
}
