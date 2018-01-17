package org.github.mitallast.blkbot

import com.google.inject.AbstractModule
import com.google.inject.Injector
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.github.mitallast.blkbot.common.component.AbstractLifecycleComponent
import org.github.mitallast.blkbot.common.component.ComponentModule
import org.github.mitallast.blkbot.common.component.ModulesBuilder
import org.github.mitallast.blkbot.common.component.LifecycleService
import org.github.mitallast.blkbot.common.events.EventBusModule
import org.github.mitallast.blkbot.common.file.FileModule
import org.github.mitallast.blkbot.common.http.HttpModule
import org.github.mitallast.blkbot.common.json.JsonModule
import org.github.mitallast.blkbot.common.netty.NettyModule
import org.github.mitallast.blkbot.exchanges.ExchangesModule

class Bot(conf: Config, vararg plugins: AbstractModule) : AbstractLifecycleComponent() {
    private val config = conf.withFallback(ConfigFactory.defaultReference())
    private val injector: Injector

    init {
        logger.info("initializing...")

        val modules = ModulesBuilder()
        modules.add(ComponentModule(config))
        modules.add(FileModule())
        modules.add(JsonModule())
        modules.add(EventBusModule())
        modules.add(NettyModule())
        modules.add(HttpModule())

        modules.add(ExchangesModule())

        modules.add(*plugins)
        injector = modules.createInjector()

        logger.info("initialized")
    }

    fun config(): Config = config

    fun injector(): Injector = injector

    override fun doStart() {
        injector.getInstance(LifecycleService::class.java).start()
    }

    override fun doStop() {
        injector.getInstance(LifecycleService::class.java).stop()
    }

    override fun doClose() {
        injector.getInstance(LifecycleService::class.java).close()
    }
}