package org.github.mitallast.blkbot.common.component

import com.google.inject.Guice
import com.google.inject.Injector
import com.google.inject.Module

import java.util.ArrayList

class ModulesBuilder {
    private val modules: MutableList<Module> = ArrayList()

    fun add(vararg modules: Module): ModulesBuilder {
        for (module in modules) {
            add(module)
        }
        return this
    }

    fun add(module: Module): ModulesBuilder {
        modules.add(module)
        return this
    }

    fun createInjector(): Injector {
        return Guice.createInjector(modules)
    }

    fun createChildInjector(injector: Injector): Injector {
        return injector.createChildInjector(modules)
    }
}
