package org.github.mitallast.blkbot.telegram

import io.netty.util.concurrent.DefaultThreadFactory
import org.github.mitallast.blkbot.common.component.AbstractLifecycleComponent
import org.github.mitallast.blkbot.exchanges.ExchangeArbitratorWorker
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject

class TelegramWorker @Inject constructor(
    private val tg: TelegramBotApi,
    private val arbitrator: ExchangeArbitratorWorker
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
                        when (message.text.orNull) {
                            "/top" -> {
                                val text = "top pairs:\n" +
                                    arbitrator.lastTop().take(20).map { p ->
                                        "- ${p.difference}% ${p.pair}" +
                                            " ${p.leftExchange}/${p.rightExchange}" +
                                            " ${p.leftPrice}/${p.rightVolume}\n"
                                    }.joinToString(separator = "")
                                tg.sendMessage(
                                    chatId = message.chat.id.toString(),
                                    text = text
                                ).await()
                            }
                        }

                    }
                }
            }
        }
    }
}