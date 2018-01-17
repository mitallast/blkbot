package org.github.mitallast.blkbot.common.http

import com.google.inject.AbstractModule

class HttpModule : AbstractModule() {

    override fun configure() {
        bind(HttpClient::class.java).asEagerSingleton()
    }
}