package org.github.mitallast.blkbot.exchanges

import io.netty.util.concurrent.DefaultThreadFactory
import org.github.mitallast.blkbot.common.component.AbstractLifecycleComponent
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class ExchangeArbitratorWorker @Inject constructor(
    private val history: ExchangeArbitrationHistory,
    private val arbitrator: ExchangeArbitrator
) : AbstractLifecycleComponent() {
    private val ec: ScheduledExecutorService
    private var future: ScheduledFuture<*>? = null

    init {
        val tf = DefaultThreadFactory("arbitrator")
        ec = Executors.newSingleThreadScheduledExecutor(tf)
    }

    override fun doStart() {
        future = ec.scheduleAtFixedRate(this::run, 0, 1, TimeUnit.MINUTES)
    }

    override fun doStop() {
        future?.cancel(false)
    }

    override fun doClose() {
        ec.shutdown()
    }

    private fun run() {
        try {
            val top = arbitrator.compute(1000).get()
            history.save(System.currentTimeMillis(), top)

            logger.info("top pairs:")
            top.forEach { p -> logger.info("${p.difference}% ${p.pair} " +
                "${p.leftExchange}/${p.rightExchange} " +
                "${p.leftPrice}/${p.rightPrice}") }
        } catch (e: Exception) {
            logger.error("error run arbitrator", e)
        }
    }
}