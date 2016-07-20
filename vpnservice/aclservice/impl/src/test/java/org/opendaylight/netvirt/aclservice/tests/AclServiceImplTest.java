/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.CONFIGURATION;
import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.OPERATIONAL;
import static org.opendaylight.netvirt.aclservice.api.tests.DataBrokerExtensions.put;
import static org.opendaylight.netvirt.aclservice.api.tests.InterfaceBuilderHelper.newInterfacePair;
import static org.opendaylight.netvirt.aclservice.api.tests.StateInterfaceBuilderHelper.newStateInterfacePair;
import static org.opendaylight.netvirt.aclservice.tests.utils.AssertBeans.assertEqualBeans;
import static org.opendaylight.netvirt.aclservice.tests.utils.MockitoNotImplementedExceptionAnswer.EXCEPTION_ANSWER;

import java.math.BigInteger;
import java.util.concurrent.Future;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.controller.config.spi.Module;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.aclservice.api.tests.AbstractAclServiceTest;
import org.opendaylight.netvirt.aclservice.api.tests.FakeIMdsalApiManager;
import org.opendaylight.netvirt.aclservice.api.tests.FlowEntryObjects;
import org.opendaylight.netvirt.aclservice.tests.idea.Mikito;
import org.opendaylight.netvirt.aclservice.tests.utils.ObjectRegistry;
import org.opendaylight.netvirt.aclservice.tests.utils.ObjectRegistry.SimpleObjectRegistry;
import org.opendaylight.netvirt.aclservice.tests.utils.ObjectRepositoryDependencyResolver;
import org.opendaylight.netvirt.aclservice.tests.utils.ObjectRepositoryProviderContext;
import org.opendaylight.netvirt.aclservice.tests.utils.ObjectRepositoryRpcProviderRegistry;
import org.opendaylight.netvirt.aclservice.tests.utils.TestBindingAwareBroker;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.impl.rev160523.AclServiceImplModuleFactory;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.osgi.framework.BundleContext;

public class AclServiceImplTest extends AbstractAclServiceTest {

    // TODO split what is in here partially up into parent AbstractAclServiceTest

    private AutoCloseable serviceProvider;

    // Services which the test depends on (either to prepare test data into, or assert things from)
    protected FakeIMdsalApiManager mdsalApiManager;

    @Before public void setUp() throws Exception {
        serviceProvider = createModuleInstance();
    }

    private AutoCloseable createModuleInstance() throws Exception {
        SimpleObjectRegistry objectRepository = new ObjectRegistry.SimpleObjectRegistry();
        objectRepository.putInstance(getDataBroker(), DataBroker.class);

        BindingAwareBroker bindingAwareBroker = Mikito.stub(TestBindingAwareBroker.class);
        objectRepository.putInstance(bindingAwareBroker, BindingAwareBroker.class);
        ObjectRepositoryRpcProviderRegistry rpcProviderRegistry = Mikito
                .stub(ObjectRepositoryRpcProviderRegistry.class);
        rpcProviderRegistry.setObjectRepository(objectRepository);
        objectRepository.putInstance(rpcProviderRegistry, RpcProviderRegistry.class);

        /// TODO The following are copy/pasted from AclServiceListenerImplTest,
        /// and this code be shared with it in *(DI?)Configuration|Module
        /// instead c/p
        mdsalApiManager = Mikito.stub(FakeIMdsalApiManager.class);
        objectRepository.putInstance(mdsalApiManager, IMdsalApiManager.class);
        // Using "classical" Mockito here (could also implement this using Mikito; useful if more complex)
        OdlInterfaceRpcService odlInterfaceRpcService = mock(OdlInterfaceRpcService.class, EXCEPTION_ANSWER);
        Future<RpcResult<GetDpidFromInterfaceOutput>> result = RpcResultBuilder
                .success(new GetDpidFromInterfaceOutputBuilder().setDpid(new BigInteger("123"))).buildFuture();
        doReturn(result).when(odlInterfaceRpcService).getDpidFromInterface(any());
        objectRepository.putInstance(odlInterfaceRpcService, OdlInterfaceRpcService.class);

        ObjectRepositoryDependencyResolver dependencyResolver = Mikito.stub(ObjectRepositoryDependencyResolver.class);
        dependencyResolver.setObjectRepository(objectRepository);

        String name = getClass().getName();
        BundleContext bundleContext = Mikito.stub(BundleContext.class);
        Module module = new AclServiceImplModuleFactory().createModule(name, dependencyResolver, bundleContext);
        AutoCloseable moduleInstance = module.getInstance();
        BindingAwareProvider serviceProviderAsBindingAwareProvider = (BindingAwareProvider) moduleInstance;
        ObjectRepositoryProviderContext sessionProviderContext = Mikito.stub(ObjectRepositoryProviderContext.class);
        sessionProviderContext.setObjectRepository(objectRepository);

        serviceProviderAsBindingAwareProvider.onSessionInitiated(sessionProviderContext);

        return moduleInstance;
    }

    @After public void tearDown() throws Exception {
        if (serviceProvider != null) {
            serviceProvider.close();
        }
    }

    @Test public void firstBigTestToLearn() throws Exception {
        put(getDataBroker(), CONFIGURATION, newInterfacePair("port1", true));
        put(getDataBroker(), OPERATIONAL, newStateInterfacePair("port1", "0D:AA:D8:42:30:F3"));

        assertEqualBeans(FlowEntryObjects.expectedFlows("0D:AA:D8:42:30:F3"), mdsalApiManager.getFlows());

        // TODO hm.... how are we going to do this, async testing, right?
        // Basically we want to wait for the events to happen, THEN assert
        // state, and timeout if none (e.g. due to errors)
        //Thread.sleep(1000000000);
    }

}
