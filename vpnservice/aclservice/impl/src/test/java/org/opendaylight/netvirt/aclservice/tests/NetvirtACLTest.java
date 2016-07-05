package org.opendaylight.netvirt.aclservice.tests;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.opendaylight.netvirt.aclservice.EgressAclServiceImpl;
import org.opendaylight.netvirt.aclservice.api.AclServiceListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAcl;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAclBuilder;

public class NetvirtACLTest
{
    // TODO eval BDD frameworks to formalize given/when/then
    // TODO like real client, go through AclServiceProvider instead of sep. EGress / Ingress as here
    // TODO use DI, perhaps Dagger &#x2021;
    // TODO mv tests into sep. new project, to enforce using only API in test, and not impl
    // TODO how to enforce coverage percentage number in build; fail if falling lower (that is how we'll guarantee.. coverage, not by explicitly calling each internal low level methods, or verifying their state)

    // TODO EMail "Augmentable's Builder addAugmentation variant without first class argument? (backward compatible)"
    // TODO How to make YANG instance object construction look nicer..
    // TODO propose using Xtend - justify why .. e.g. useful for object creation with Builders?

    // DOC: For readability, no private etc. modifiers, unless public required by JUnit (bah)

    AclServiceListener aclService;

    @Before public void setUp() {
        aclService = new EgressAclServiceImpl(null, null, null);
    }

    @Test public void applyToPortWithoutSecurityGroup() {
        Interface port1 = new InterfaceBuilder().build();
        assertFalse(aclService.applyAcl(port1));
    }

    @Test public void applyToPortWithSecurityDisabled() {
        InterfaceAcl acl1 = new InterfaceAclBuilder().setPortSecurityEnabled(false).build();
        Interface port1 = new InterfaceBuilder().addAugmentation(InterfaceAcl.class, acl1).build();
        assertFalse(aclService.applyAcl(port1));
    }

    @Test public void applyToPortWithSecurityEnabled() {
        InterfaceAcl acl1 = new InterfaceAclBuilder().setPortSecurityEnabled(true).build();
        Interface port1 = new InterfaceBuilder().addAugmentation(InterfaceAcl.class, acl1).build();
        assertTrue(aclService.applyAcl(port1));
        // assert more shit happened (in datastore), as expected
    }

}
