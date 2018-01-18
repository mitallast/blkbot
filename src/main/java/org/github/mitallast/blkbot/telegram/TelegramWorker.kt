package org.github.mitallast.blkbot.telegram

import io.netty.util.concurrent.DefaultThreadFactory
import org.github.mitallast.blkbot.common.component.AbstractLifecycleComponent
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject

class TelegramWorker @Inject constructor(
        private val tg: TelegramBotApi
) : AbstractLifecycleComponent() {
    private val ec: ExecutorService

    init {
        val tf = DefaultThreadFactory("telegram")
        ec = Executors.newSingleThreadExecutor(tf)
    }

    override fun doStart() {
        ec.execute(this::run)
    }

    override fun doStop() {}

    override fun doClose() {
        ec.shutdown()
    }

    private fun run() {
        logger.info("start worker")
        var offset: Long? = null
        while (lifecycle().started()) {
            val updates = tg.getUpdates(offset = offset, timeout = 100000).await().get()
            for (update in updates) {
                offset = update.updateId + 1
                when {
                    update.message.isDefined -> {
                        val message = update.message.get()
                        tg.sendMessage(
                                chatId = message.chat.id.toString(),
                                text = "Hello world"
                        ).await().onComplete { r -> println(r) }
                    }
                }
            }
        }
    }
}