package org.github.mitallast.blkbot.common.file

import com.google.inject.AbstractModule

class FileModule : AbstractModule() {
    override fun configure() {
        bind(FileService::class.java).asEagerSingleton()
    }
}
