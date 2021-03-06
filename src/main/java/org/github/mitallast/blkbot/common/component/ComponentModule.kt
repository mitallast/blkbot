package org.github.mitallast.blkbot.common.component

import com.google.inject.AbstractModule
import com.typesafe.config.Config

class ComponentModule(private val config: Config) : AbstractModule() {

    override fun configure() {
        val lifecycleService = LifecycleService()
        bind(Config::class.java).toInstance(config)
        bind(LifecycleService::class.java).toInstance(lifecycleService)
        bindListener(LifecycleMatcher(), lifecycleService)
    }
}

