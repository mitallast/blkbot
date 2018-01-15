package org.github.mitallast.blkbot.exchanges

import com.google.inject.AbstractModule
import org.github.mitallast.blkbot.common.netty.NettyProvider

class ExchangesModule() : AbstractModule() {

    override fun configure() {
        bind(NettyProvider::class.java).asEagerSingleton()
    }
}

