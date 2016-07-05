/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests;

import dagger.Component;
import javax.inject.Singleton;
import org.junit.After;
import org.junit.Before;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.mdsalutil.interfaces.testutils.TestIMdsalApiManager;
import org.opendaylight.netvirt.aclservice.AclServiceImplFactory;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager;
import org.opendaylight.netvirt.aclservice.api.tests.AbstractAclServiceTest;

public class AclServiceTest extends AbstractAclServiceTest {

    @Singleton
    @Component(modules = { AclServiceModule.class, AclServiceTestModule.class })
    interface TestComponent {
        AclServiceManager aclServiceManager();

        DataBroker dataBroker();

        TestIMdsalApiManager testIMdsalApiManager();

        @Singleton AclServiceImplFactory aclServiceImplFactory();
    }

    @Before
    public void setUp() {
        TestComponent testComponent = DaggerAclServiceTest_TestComponent.builder().build();
        dataBroker = testComponent.dataBroker();
        mdsalApiManager = testComponent.testIMdsalApiManager();
    }

    @After
    public void tearDown() throws Exception {
//        if (testModule != null) {
//            testModule.close();
//        }
    }

}
