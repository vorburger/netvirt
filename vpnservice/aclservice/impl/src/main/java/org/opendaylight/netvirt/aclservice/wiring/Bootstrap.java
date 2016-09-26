/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.wiring;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.mycila.guice.ext.closeable.CloseableInjector;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.config.rev160806.AclserviceConfig;

/**
 * Boot this bundle by glueing Blueprint & Guice.
 *
 * @author Michael Vorburger
 */
public class Bootstrap {

    // TODO Do this with DS annotations instead of BP XML, to simplify this

    private final DataBroker dataBroker;
    private final IMdsalApiManager mdsalApiManager;
    private final EntityOwnershipService entityOwnershipService;
    private final AclserviceConfig aclServiceConfig;

    private final Injector injector;

    public Bootstrap(DataBroker dataBroker, IMdsalApiManager mdsalApiManager,
            EntityOwnershipService entityOwnershipService, AclserviceConfig aclServiceConfig) {
        super();
        this.dataBroker = dataBroker;
        this.mdsalApiManager = mdsalApiManager;
        this.entityOwnershipService = entityOwnershipService;
        this.aclServiceConfig = aclServiceConfig;

        injector = Guice.createInjector(Stage.PRODUCTION, new BlueprintModule(), new AclServiceModule());
    }

    // TODO integrate above and below with new infrautils.inject.guice

    public void stop() {
        // http://code.mycila.com/guice/#3-jsr-250
        injector.getInstance(CloseableInjector.class).close();
    }

    public class BlueprintModule extends AbstractModule {
        @Override
        protected void configure() {
            bind(DataBroker.class).toInstance(dataBroker);
            bind(IMdsalApiManager.class).toInstance(mdsalApiManager);
            bind(EntityOwnershipService.class).toInstance(entityOwnershipService);
            bind(AclserviceConfig.class).toInstance(aclServiceConfig);
        }
    }
}
