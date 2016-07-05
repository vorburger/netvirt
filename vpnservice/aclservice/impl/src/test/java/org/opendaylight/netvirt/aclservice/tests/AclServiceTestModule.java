/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import org.mockito.Mockito;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.test.DataBrokerTestModule;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.interfaces.testutils.TestIMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.config.rev160806.AclserviceConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.config.rev160806.AclserviceConfig.SecurityGroupMode;

@Module
public abstract class AclServiceTestModule {

    @Binds abstract IMdsalApiManager mdsalApiManager(TestIMdsalApiManager impl);

    @Provides static DataBroker dataBroker() {
        return DataBrokerTestModule.dataBroker();
    }

    @Provides static TestIMdsalApiManager testIMdsalApiManager() {
        return TestIMdsalApiManager.newInstance();
    }

    @Provides static AclserviceConfig aclServiceConfig() {
        AclserviceConfig aclServiceConfig = Mockito.mock(AclserviceConfig.class);
        Mockito.when(aclServiceConfig.getSecurityGroupMode()).thenReturn(SecurityGroupMode.Transparent);
        return aclServiceConfig;
    }

}
