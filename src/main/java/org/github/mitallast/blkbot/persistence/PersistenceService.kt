package org.github.mitallast.blkbot.persistence

import com.google.inject.Inject
import com.typesafe.config.Config
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.pool.HikariPool
import org.flywaydb.core.Flyway
import org.github.mitallast.blkbot.common.component.AbstractLifecycleComponent
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.tools.jdbc.JDBCUtils
import java.sql.Timestamp
import java.util.*

class PersistenceService @Inject constructor(config: Config) : AbstractLifecycleComponent() {
    private val conf = config.getConfig("persistence")
    private val url = conf.getString("url")
    private val username =
        if (conf.getIsNull("username")) null
        else conf.getString("username")
    private val password =
        if (conf.getIsNull("password")) null
        else conf.getString("password")
    private val await = conf.getDuration("await").toMillis()
    private val dialect = JDBCUtils.dialect(url)
    private val properties = Properties()
    private val dataSource: HikariDataSource

    init {
        for ((key, value) in conf.getConfig("properties").entrySet()) {
            properties.put(key, value.unwrapped())
        }

        val conf = HikariConfig(properties)
        conf.jdbcUrl = url
        conf.username = username
        conf.password = password

        dataSource = HikariDataSource(conf)
        await()
        migrate()
    }

    private fun await() {
        val start = System.currentTimeMillis()
        val timeout = start + await
        var lastError: Throwable? = null
        logger.info("check database connection")
        while (System.currentTimeMillis() < timeout) {
            try {
                dataSource.connection.use { connection ->
                    if (!connection.autoCommit) {
                        connection.commit()
                    }
                    logger.info("successful connect to database")
                    return
                }
            } catch (e: Throwable) {
                logger.warn("failed retrieve connection, await")
                lastError = e
                Thread.sleep(1000)
            }
        }
        if (lastError != null) {
            throw HikariPool.PoolInitializationException(lastError)
        }
    }

    private fun migrate() {
        val flyway = Flyway();
        flyway.dataSource = dataSource
        flyway.migrate();
    }

    fun context(): DSLContext {
        return DSL.using(dataSource, dialect)
    }

    fun now(): Timestamp = timestamp(System.currentTimeMillis())

    fun timestamp(millis: Long): Timestamp = Timestamp(millis)

    override fun doStart() {}

    override fun doStop() {}

    override fun doClose() {
        dataSource.close()
    }
}
