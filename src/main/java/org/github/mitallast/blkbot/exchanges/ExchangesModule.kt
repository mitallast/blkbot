package org.github.mitallast.blkbot.exchanges

import com.google.inject.AbstractModule
import org.github.mitallast.blkbot.exchanges.binance.BinanceClient
import org.github.mitallast.blkbot.exchanges.bittrex.BittrexClient
import org.github.mitallast.blkbot.exchanges.cryptopia.CryptopiaClient

class ExchangesModule() : AbstractModule() {
    override fun configure() {
        bind(BinanceClient::class.java).asEagerSingleton()
        bind(BittrexClient::class.java).asEagerSingleton()
        bind(CryptopiaClient::class.java).asEagerSingleton()
    }
}
