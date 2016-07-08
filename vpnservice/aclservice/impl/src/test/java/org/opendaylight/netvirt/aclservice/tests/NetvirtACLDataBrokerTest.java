/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.OPERATIONAL;
import static org.opendaylight.netvirt.aclservice.tests.utils.MockitoNotImplementedExceptionAnswer.ExceptionAnswer;

import java.math.BigInteger;
import java.util.concurrent.Future;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.test.AbstractDataBrokerTest;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.netvirt.aclservice.AclServiceUtils;
import org.opendaylight.netvirt.aclservice.EgressAclServiceImpl;
import org.opendaylight.netvirt.aclservice.api.AclServiceListener;
import org.opendaylight.netvirt.aclservice.tests.idea.Mikito;
import org.opendaylight.netvirt.aclservice.tests.utils.FakeIMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAcl;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAclBuilder;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;

public class NetvirtACLDataBrokerTest extends AbstractDataBrokerTest {

    // TODO test more, see coverage - what's missing for ~100% ?
    // TODO avoid extends AbstractDataBrokerTest, use @Rule instead
    // TODO try creating DataBroker stub/mock/fake (?), using Mikito

    // TODO eval BDD frameworks to formalize given/when/then
    // TODO like real client, go through AclServiceProvider instead of sep. EGress / Ingress as here
    // TODO use DI, perhaps Dagger &#x2021;
    // TODO YANG model data objects created in tests should be validated programmatically? mandatory true; (and keys?) are @NonNull ..

    // TODO mv tests into sep. new project, to enforce using only API in test, and not impl. With <parent> that already has <dependencies> sal-binding-broker-impl & slf4j-simple etc.
    // TODO how to enforce coverage percentage number in build; fail if falling lower (that is how we'll guarantee.. coverage, not by explicitly calling each internal low level methods, or verifying their state)

    // TODO EMail "Augmentable's Builder addAugmentation variant without first class argument? (backward compatible)"
    // TODO How to make YANG instance object construction look nicer..
    // TODO propose using Xtend - justify why .. e.g. useful for object creation with Builders?
    // TODO how about a textual format for defining YANG store content at the start of tests?

    // DOC: For readability, no private etc. modifiers, unless public required by JUnit (bah)

    // DOC [unless perf resolved]
    // TODO lazy DataBroker Provider, so that @Test which do not need it don't hit perf. overhead, yet can still stay in same *Test class - is that possible?

    // Service under test
    AclServiceListener aclService;

    // Mocked other services which the main service under test depends on
    DataBroker dataBroker;
    OdlInterfaceRpcService odlInterfaceRpcService;
    FakeIMdsalApiManager mdsalApiManager;

    @Before public void setUp() {
        dataBroker = getDataBroker();

        // TODO Personally I'd rather use my Mikito approach here .. but discuss it with others, first
        odlInterfaceRpcService = mock(OdlInterfaceRpcService.class, ExceptionAnswer);
        Future<RpcResult<GetDpidFromInterfaceOutput>> result = RpcResultBuilder.success(new GetDpidFromInterfaceOutputBuilder().setDpid(new BigInteger("123"))).buildFuture();
        doReturn(result).when(odlInterfaceRpcService).getDpidFromInterface(any());

        mdsalApiManager = Mikito.stub(FakeIMdsalApiManager.class);

        aclService = new EgressAclServiceImpl(dataBroker, odlInterfaceRpcService, mdsalApiManager);
    }

    @Test public void applyToPortWithSecurityEnabled() {
        InterfaceAcl acl1 = new InterfaceAclBuilder().setPortSecurityEnabled(true).build();
        Interface port1 = new InterfaceBuilder().addAugmentation(InterfaceAcl.class, acl1).setName("port1").build();

        // TODO write a better helper to avoid duplicating "port1" name/Id - this should be implicit?
        // TODO ultimately I probably won't want to use the (internal!) AclServiceUtils here.. see later how to simplify creating these IDs
        StateInterfaceBuilder builder = new StateInterfaceBuilder().setName("port1").setPhysAddress(new PhysAddress("0D:AA:D8:42:30:F3"));
        MDSALUtil.syncWrite(dataBroker, OPERATIONAL, AclServiceUtils.buildStateInterfaceId("port1"), builder.build());

        // NOTE Typically no real strong good need to use [Mockito's] verify() on odlInterfaceRpcService here.. remember, focus on testing the API outcome, not the implementation detail of what dependent service got called.

        assertTrue(aclService.applyAcl(port1));
        assertEquals(10, mdsalApiManager.getFlows().size());
        // TODO Make FlowEntry have proper equals (and hashCode) and remove toString.. see below
        assertEquals(FlowEntryObjects.flow1().toString(), mdsalApiManager.getFlows().get(0).toString());
        // TODO Must also assert remaining nine flows!
    }

    @Ignore
    @Test public void compareFlowEntryWithEquals() {
        assertEquals(FlowEntryObjects.flow1(), FlowEntryObjects.flow1());
    }

    @Test public void compareFlowEntryWithToString() {
        assertEquals(FlowEntryObjects.flow1().toString(), FlowEntryObjects.flow1().toString());
    }

}
