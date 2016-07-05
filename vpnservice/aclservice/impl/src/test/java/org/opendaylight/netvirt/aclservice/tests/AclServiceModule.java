/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests;

import dagger.Binds;
import dagger.Module;
import org.opendaylight.netvirt.aclservice.AclServiceManagerImpl;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager;

/**
 * Dagger2-based Dependency Injection (DI) Wiring, in 2016 syntax (i.e. Java;
 * strongly typed!) instead of late 1990s XML syntax as in Blueprint XML.
 *
 * @author Michael Vorburger
 */
@Module
public interface AclServiceModule {

/*
    public abstract DataBroker dataBroker();
    public abstract IMdsalApiManager mdsalApiManager();
    public abstract AclserviceConfig aclServiceConfig();

    private AclServiceManagerImpl aclServiceManager;
    private IngressAclServiceImpl ingressAclService;
    private EgressAclServiceImpl egressAclService;
    private AclInterfaceStateListener aclInterfaceStateListener;
    private AclNodeListener aclNodeListener;
    private AclInterfaceListener aclInterfaceListener;
    private AclEventListener aclEventListener;
*/
    @Binds AclServiceManager aclServiceManager(AclServiceManagerImpl impl);

/*
    public IngressAclServiceImpl ingressAclService() {
        if (ingressAclService == null) {
            ingressAclService = new IngressAclServiceImpl(dataBroker(), mdsalApiManager());
        }
        return ingressAclService;
    }

    public EgressAclServiceImpl egressAclService() {
        if (egressAclService == null) {
            egressAclService = new EgressAclServiceImpl(dataBroker(), mdsalApiManager());
        }
        return egressAclService;
    }

    public AclInterfaceStateListener aclInterfaceStateListener() {
        if (aclInterfaceStateListener == null) {
            aclInterfaceStateListener = new AclInterfaceStateListener(aclServiceManager(), dataBroker());
            aclInterfaceStateListener.start();
        }
        return aclInterfaceStateListener;
    }

    public AclNodeListener aclNodeListener() {
        if (aclNodeListener == null) {
            aclNodeListener = new AclNodeListener(mdsalApiManager(), dataBroker(), aclServiceConfig());
            aclNodeListener.start();
        }
        return aclNodeListener;
    }

    public AclInterfaceListener aclInterfaceListener() {
        if (aclInterfaceListener == null) {
            aclInterfaceListener = new AclInterfaceListener(aclServiceManager(), dataBroker());
            aclInterfaceListener.start();
        }
        return aclInterfaceListener;
    }

    public AclEventListener aclEventListener() {
        if (aclEventListener == null) {
            aclEventListener = new AclEventListener(aclServiceManager(), dataBroker());
            aclEventListener.start();
        }
        return aclEventListener;
    }
*/
}
