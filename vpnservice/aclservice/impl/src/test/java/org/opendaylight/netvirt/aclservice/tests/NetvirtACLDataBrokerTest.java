package org.opendaylight.netvirt.aclservice.tests;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.opendaylight.netvirt.aclservice.tests.utils.MockitoNotImplementedExceptionAnswer.ExceptionAnswer;

import java.math.BigInteger;
import java.util.concurrent.Future;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.controller.md.sal.binding.test.AbstractDataBrokerTest;
import org.opendaylight.netvirt.aclservice.EgressAclServiceImpl;
import org.opendaylight.netvirt.aclservice.api.AclServiceListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAcl;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAclBuilder;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;

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


    @Before public void setUp() {
        // TODO Personally I'd rather use my Mikito approach here .. but discuss it with others, first
        odlInterfaceRpcService = mock(OdlInterfaceRpcService.class, ExceptionAnswer);
        Future<RpcResult<GetDpidFromInterfaceOutput>> result = RpcResultBuilder.success(new GetDpidFromInterfaceOutputBuilder().setDpid(new BigInteger("123"))).buildFuture();
        doReturn(result).when(odlInterfaceRpcService).getDpidFromInterface(any());

        aclService = new EgressAclServiceImpl(getDataBroker(), odlInterfaceRpcService, null);
    }

    @Test public void applyToPortWithSecurityEnabled() {
        InterfaceAcl acl1 = new InterfaceAclBuilder().setPortSecurityEnabled(true).build();
        Interface port1 = new InterfaceBuilder().addAugmentation(InterfaceAcl.class, acl1).setName("port1").build();
        assertTrue(aclService.applyAcl(port1));
        // assert more shit happened (in datastore), as expected
    }

}
