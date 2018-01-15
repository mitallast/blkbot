package org.github.mitallast.blkbot.common.netty

import com.google.inject.AbstractModule

class NettyModule : AbstractModule() {
    override fun configure() {
        bind(NettyProvider::class.java).asEagerSingleton()
    }
}
