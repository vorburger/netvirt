package org.opendaylight.netvirt.aclservice.tests;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.opendaylight.controller.md.sal.binding.test.AbstractDataBrokerTest;
import org.opendaylight.netvirt.aclservice.EgressAclServiceImpl;
import org.opendaylight.netvirt.aclservice.api.AclServiceListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAcl;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAclBuilder;

public class NetvirtACLDataBrokerTest extends AbstractDataBrokerTest {

    // TODO eval BDD frameworks to formalize given/when/then
    // TODO like real client, go through AclServiceProvider instead of sep. EGress / Ingress as here
    // TODO use DI, perhaps Dagger &#x2021;
    // TODO YANG model data objects created in tests should be validated programmatically? mandatory true; (and keys?) are @NonNull ..

    // TODO mv tests into sep. new project, to enforce using only API in test, and not impl. With <parent> that already has <dependencies> sal-binding-broker-impl & slf4j-simple etc.
    // TODO how to enforce coverage percentage number in build; fail if falling lower (that is how we'll guarantee.. coverage, not by explicitly calling each internal low level methods, or verifying their state)

    // TODO EMail "Augmentable's Builder addAugmentation variant without first class argument? (backward compatible)"
    // TODO How to make YANG instance object construction look nicer..
    // TODO propose using Xtend - justify why .. e.g. useful for object creation with Builders?

    // DOC: For readability, no private etc. modifiers, unless public required by JUnit (bah)

    // DOC [unless perf resolved]
    // TODO lazy DataBroker Provider, so that @Test which do not need it don't hit perf. overhead, yet can still stay in same *Test class - is that possible?

    // Service under test
    AclServiceListener aclService;

    // Mocked other services which the main service under test depends on
    OdlInterfaceRpcService odlInterfaceRpcService;



    // mock getDpidFromInterface

    @Before public void setUp() {
        aclService = new EgressAclServiceImpl(getDataBroker(), odlInterfaceRpcService, null);

        odlInterfaceRpcService = Mockito.mock(OdlInterfaceRpcService.class);
        // TODO .getDpidFromInterface(null)
    }

    @Test public void applyToPortWithSecurityEnabled() {
        InterfaceAcl acl1 = new InterfaceAclBuilder().setPortSecurityEnabled(true).build();
        Interface port1 = new InterfaceBuilder().addAugmentation(InterfaceAcl.class, acl1).setName("port1").build();
        assertTrue(aclService.applyAcl(port1));
        // assert more shit happened (in datastore), as expected
    }

}
