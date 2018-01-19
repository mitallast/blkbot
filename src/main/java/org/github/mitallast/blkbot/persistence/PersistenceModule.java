package org.github.mitallast.blkbot.persistence;

import com.google.inject.AbstractModule;

public class PersistenceModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(PersistenceService.class).asEagerSingleton();
    }
}
