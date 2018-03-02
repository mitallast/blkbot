package org.github.mitallast.blkbot.exchanges

import io.netty.util.concurrent.DefaultThreadFactory
import io.vavr.collection.Vector
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
    @Volatile
    private var future: ScheduledFuture<*>? = null
    @Volatile
    private var top: Vector<ExchangeArbitrationPair> = Vector.empty()

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

    fun lastTop(): Vector<ExchangeArbitrationPair> = top

    private fun run() {
        try {
            top = arbitrator.compute(1000).get()
            history.save(System.currentTimeMillis(), top)

            println("top pairs:")
            top.forEach { p ->
                println("${p.difference}% ${p.pair} " +
                    "${p.leftExchange}/${p.rightExchange} " +
                    "price:${p.leftPrice}/${p.rightPrice} " +
                    "volB:${p.leftVolumeBase}/${p.rightVolumeBase} " +
                    "volQ:${p.leftVolumeQuote}/${p.rightVolumeQuote} " +
                    "bid:${p.leftBid}/${p.rightBid} " +
                    "ask:${p.leftAsk}/${p.rightAsk}"
                )
            }
        } catch (e: Exception) {
            logger.error("error run arbitrator", e)
        }
    }
}