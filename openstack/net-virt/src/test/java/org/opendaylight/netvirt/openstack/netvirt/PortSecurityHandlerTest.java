/*
 * Copyright (c) 2015, 2016 Inocybe and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.openstack.netvirt;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.net.HttpURLConnection;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.opendaylight.netvirt.openstack.netvirt.api.EventDispatcher;
import org.opendaylight.netvirt.openstack.netvirt.translator.NeutronSecurityGroup;
import org.opendaylight.netvirt.openstack.netvirt.translator.NeutronSecurityRule;
import org.opendaylight.netvirt.utils.servicehelper.ServiceHelper;
import org.osgi.framework.ServiceReference;

/**
 * Unit test {@link PortSecurityHandler}
 */
@RunWith(MockitoJUnitRunner.class)
public class PortSecurityHandlerTest {

    @InjectMocks private PortSecurityHandler portSecurityHandler;
    private PortSecurityHandler posrtSecurityHandlerSpy;
    @Mock
    EventDispatcher eventDispatcher;

    @Before
    public void setUp() {
        posrtSecurityHandlerSpy = Mockito.spy(portSecurityHandler);
    }

    @Test
    public void testCanCreateNeutronSecurityGroup() {
        assertEquals("Error, did not return the correct HTTP flag", HttpURLConnection.HTTP_CREATED, portSecurityHandler.canCreateNeutronSecurityGroup(mock(NeutronSecurityGroup.class)));

        posrtSecurityHandlerSpy.neutronSecurityGroupCreated(any(NeutronSecurityGroup.class));
        verify(posrtSecurityHandlerSpy, times(1)).canCreateNeutronSecurityGroup(any(NeutronSecurityGroup.class));
    }

    @Test
    public void testCanUpdateNeutronSecurityGroup() {
        assertEquals("Error, did not return the correct HTTP flag", HttpURLConnection.HTTP_OK, portSecurityHandler.canUpdateNeutronSecurityGroup(mock(NeutronSecurityGroup.class), mock(NeutronSecurityGroup.class)));
    }

    @Test
    public void testCanDeleteNeutronSecurityGroup() {
        assertEquals("Error, did not return the correct HTTP flag", HttpURLConnection.HTTP_OK, portSecurityHandler.canDeleteNeutronSecurityGroup(mock(NeutronSecurityGroup.class)));

        posrtSecurityHandlerSpy.neutronSecurityGroupDeleted(any(NeutronSecurityGroup.class));
        verify(posrtSecurityHandlerSpy, times(1)).canDeleteNeutronSecurityGroup(any(NeutronSecurityGroup.class));
    }

    @Test
    public void testCanCreateNeutronSecurityRule() {
        assertEquals("Error, did not return the correct HTTP flag", HttpURLConnection.HTTP_CREATED, portSecurityHandler.canCreateNeutronSecurityRule(mock(NeutronSecurityRule.class)));

        posrtSecurityHandlerSpy.neutronSecurityRuleCreated(any(NeutronSecurityRule.class));
        verify(posrtSecurityHandlerSpy, times(1)).enqueueEvent(any(AbstractEvent.class));
    }

    @Test
    public void testCanUpdateNeutronSecurityRule() {
        assertEquals("Error, did not return the correct HTTP flag", HttpURLConnection.HTTP_OK, portSecurityHandler.canUpdateNeutronSecurityRule(mock(NeutronSecurityRule.class), mock(NeutronSecurityRule.class)));
    }

    @Test
    public void testCanDeleteNeutronSecurityRule() {
        assertEquals("Error, did not return the correct HTTP flag", HttpURLConnection.HTTP_OK, portSecurityHandler.canDeleteNeutronSecurityRule(mock(NeutronSecurityRule.class)));

        posrtSecurityHandlerSpy.neutronSecurityRuleDeleted(any(NeutronSecurityRule.class));
        verify(posrtSecurityHandlerSpy, times(1)).enqueueEvent(any(AbstractEvent.class));
   }

    @Test
    public void testProcessEvent() {
        // TODO see PortSecurityHandler#processEvent()
    }

    @Test
    public void testSetDependencies() throws Exception {
        EventDispatcher eventDispatcher = mock(EventDispatcher.class);

        ServiceHelper.overrideGlobalInstance(EventDispatcher.class, eventDispatcher);

        portSecurityHandler.setDependencies(mock(ServiceReference.class));

        Assert.assertEquals("Error, did not return the correct object", portSecurityHandler.eventDispatcher, eventDispatcher);
    }


    private Object getField(String fieldName) throws Exception {
        Field field = PortSecurityHandler.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(portSecurityHandler);
    }

}
