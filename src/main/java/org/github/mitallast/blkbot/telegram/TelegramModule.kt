package org.github.mitallast.blkbot.telegram

import com.google.inject.AbstractModule

class TelegramModule() : AbstractModule() {
    override fun configure() {
        bind(TelegramBotApi::class.java).asEagerSingleton()
        bind(TelegramWorker::class.java).asEagerSingleton()
    }
}