/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elanmanager.api.tests;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.netvirt.elanmanager.api.IElanService;

/**
 * End-to-end test of IElanService.
 *
 * @author Michael Vorburger
 */
public class IElanServiceTest {

    IElanService elanService;

    @Test
    public void createElanInstance() {
        elanService.createElanInstance("TestELanName", 12345, "TestELan description");
    }

    @Before
    public void setUp() {
        testModule = new AclServiceTestModule();
        dataBroker = testModule.dataBroker();
        mdsalApiManager = testModule.mdsalApiManager();
        testModule.start();
    }

    @After
    public void tearDown() throws Exception {
        if (testModule != null) {
            testModule.close();
        }
    }
}
