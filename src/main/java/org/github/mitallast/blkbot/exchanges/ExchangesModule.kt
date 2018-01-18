package org.github.mitallast.blkbot.exchanges

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import org.github.mitallast.blkbot.exchanges.binance.BinanceClient
import org.github.mitallast.blkbot.exchanges.binance.BinanceTradeProvider
import org.github.mitallast.blkbot.exchanges.bittrex.BittrexClient
import org.github.mitallast.blkbot.exchanges.bittrex.BittrexTradeProvider
import org.github.mitallast.blkbot.exchanges.cryptopia.CryptopiaClient
import org.github.mitallast.blkbot.exchanges.cryptopia.CryptopiaTradeProvider
import org.github.mitallast.blkbot.exchanges.hitbtc.HitbtcClient
import org.github.mitallast.blkbot.exchanges.hitbtc.HitbtcTradeProvider


class ExchangesModule : AbstractModule() {
    override fun configure() {
        bind(BinanceClient::class.java).asEagerSingleton()
        bind(BittrexClient::class.java).asEagerSingleton()
        bind(CryptopiaClient::class.java).asEagerSingleton()
        bind(HitbtcClient::class.java).asEagerSingleton()

        val providers = Multibinder.newSetBinder(binder(), ExchangeTradeProvider::class.java)
        providers.addBinding().to(BinanceTradeProvider::class.java).asEagerSingleton()
        providers.addBinding().to(BittrexTradeProvider::class.java).asEagerSingleton()
        providers.addBinding().to(CryptopiaTradeProvider::class.java).asEagerSingleton()
        providers.addBinding().to(HitbtcTradeProvider::class.java).asEagerSingleton()

        bind(ExchangeArbitrator::class.java).asEagerSingleton()
    }
}
