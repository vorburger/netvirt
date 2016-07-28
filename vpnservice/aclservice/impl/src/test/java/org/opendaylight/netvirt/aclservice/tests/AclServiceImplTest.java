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

import dagger.Component;
import dagger.MembersInjector;
import dagger.Provides;
import java.math.BigInteger;
import java.util.concurrent.Future;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.opendaylight.controller.config.spi.ModuleFactory;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.aclservice.api.tests.AbstractAclServiceTest;
import org.opendaylight.netvirt.aclservice.api.tests.FlowEntryObjects;
import org.opendaylight.netvirt.aclservice.api.tests.TestIMdsalApiManager;
import org.opendaylight.netvirt.aclservice.tests.idea.Mikito;
import org.opendaylight.netvirt.aclservice.tests.utils.DataBrokerTestModule;
import org.opendaylight.netvirt.aclservice.tests.utils.ObjectRegistry;
import org.opendaylight.netvirt.aclservice.tests.utils.dags.AbstractBindingAndConfigTestModule;
import org.opendaylight.netvirt.aclservice.tests.utils.inject.junit.InjectorRule;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.impl.rev160523.AclServiceImplModuleFactory;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;

public class AclServiceImplTest extends AbstractAclServiceTest {

    // TODO split what is in here partially up into parent
    // AbstractAclServiceTest

    @dagger.Module
    static class TestDependenciesModule {
        @Singleton
        @Provides
        OdlInterfaceRpcService odlInterfaceRpcService(ObjectRegistry.Builder registryBuilder) {
            // Using "classical" Mockito here (could also implement this using
            // Mikito; useful if more complex; both are perfectly possible).
            OdlInterfaceRpcService odlInterfaceRpcService = mock(OdlInterfaceRpcService.class, EXCEPTION_ANSWER);
            registryBuilder.putInstance(odlInterfaceRpcService, OdlInterfaceRpcService.class);
            return odlInterfaceRpcService;
        }

        @Singleton
        @Provides
        TestIMdsalApiManager fakeMdsalApiManager(ObjectRegistry.Builder registryBuilder) {
            TestIMdsalApiManager mdsalApiManager = Mikito.stub(TestIMdsalApiManager.class);
            registryBuilder.putInstance(mdsalApiManager, IMdsalApiManager.class);
            return mdsalApiManager;
        }

        @Provides
        IMdsalApiManager mdsalApiManager(TestIMdsalApiManager fake) {
            return fake;
        }
    }

    @dagger.Module
    static class BindingAndConfigTestModule extends AbstractBindingAndConfigTestModule {
        @Override
        protected ModuleFactory moduleFactory() {
            return new AclServiceImplModuleFactory();
        }
    }

    @Singleton
    @Component(modules = { TestDependenciesModule.class, BindingAndConfigTestModule.class, DataBrokerTestModule.class })
    interface Configuration extends MembersInjector<AclServiceImplTest> {
        @Override
        void injectMembers(AclServiceImplTest test);
    }

    @Rule public InjectorRule injector = new InjectorRule(DaggerAclServiceImplTest_Configuration.create());

    @Inject DataBroker dataBroker;
    @Inject TestIMdsalApiManager mdsalApiManager;
    @Inject OdlInterfaceRpcService odlInterfaceRpcService;
    @Inject AutoCloseable serviceProvider;

    @After
    public void tearDown() throws Exception {
        if (serviceProvider != null) {
            serviceProvider.close();
        }
    }

    @Test
    public void newInterface() throws Exception {
        // Given
        put(dataBroker, CONFIGURATION, newInterfacePair("port1", true));

        Future<RpcResult<GetDpidFromInterfaceOutput>> result = RpcResultBuilder
                .success(new GetDpidFromInterfaceOutputBuilder().setDpid(new BigInteger("123"))).buildFuture();
        doReturn(result).when(odlInterfaceRpcService).getDpidFromInterface(any());

        // When
        put(dataBroker, OPERATIONAL, newStateInterfacePair("port1", "0D:AA:D8:42:30:F3"));

        // Then
        // TODO must do better synchronization here.. this is multi-thread, must
        // wait for completion - how-to? Use https://github.com/awaitility/awaitility, but wait on what?
        assertEqualBeans(FlowEntryObjects.expectedFlows("0D:AA:D8:42:30:F3"), mdsalApiManager.getFlows());
    }

}
