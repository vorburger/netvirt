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

public class NetvirtACLUnitTest {

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

}
