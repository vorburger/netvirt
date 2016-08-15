/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elanmanager.api.tests;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.idmanager.IdManager;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.netvirt.elan.internal.ElanBridgeManager;
import org.opendaylight.netvirt.elan.internal.ElanInstanceManager;
import org.opendaylight.netvirt.elan.internal.ElanInterfaceManager;
import org.opendaylight.netvirt.elan.internal.ElanServiceProvider;
import org.opendaylight.netvirt.elan.statusanddiag.ElanStatusMonitor;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;

/**
 * Equivalent of src/main/resources/org/opendaylight/blueprint/elanmanager.xml,
 * in 2016 syntax (i.e. Java; strongly typed!) instead of late 1990s XML syntax.
 *
 * @author Michael Vorburger
 */
public class ElanServiceModule implements AutoCloseable {

    // TODO Later, post-merge, propose replacing this kind of code with Dagger generated cod
    // (as originally proposed in https://git.opendaylight.org/gerrit/#/c/42109/

    // TODO Later, post-merge, propose replacing the non-OSGi part of elanmanager.xml with this
    // as proposed e.g. on https://git.opendaylight.org/gerrit/#/c/43754/

    private final DataBroker dataBroker;

    private ElanServiceProvider elanService;
    private IdManager idManager;
    @SuppressWarnings("deprecation")
    private IInterfaceManager interfaceManager;
    private ElanInstanceManager elanInstanceManager;
    private ElanBridgeManager bridgeMgr;
    private ElanInterfaceManager elanInterfaceManager;
    private ElanStatusMonitor elanStatusMonitor;
    private ElanUtils elanUtils;

    public ElanServiceModule(DataBroker dataBroker) {
        super();
        this.dataBroker = dataBroker;
    }

    public IElanService elanService() {
        if (elanService == null) {
            elanService = new ElanServiceProvider(idManager(), interfaceManager(), elanInstanceManager(), bridgeMgr(),
                    dataBroker, elanInterfaceManager(), elanStatusMonitor(), elanUtils());
            elanService.init();
        }
        return elanService();
    }

    private ElanStatusMonitor elanStatusMonitor() {
    	if (elanStatusMonitor == null) {
    		elanStatusMonitor = new ElanStatusMonitor();
    	}
		return elanStatusMonitor;
	}

	private ElanInterfaceManager elanInterfaceManager() {
        if (elanInterfaceManager == null) {
            elanInterfaceManager = new ElanInterfaceManager(dataBroker, managerService(), mdsalApiManager(), interfaceManager(), elanForwardingEntriesHandler());
        }
        return elanInterfaceManager;
    }

    private ElanBridgeManager bridgeMgr() {
        if (bridgeMgr == null) {
            bridgeMgr = new ElanBridgeManager(dataBroker);
        }
        return bridgeMgr;
    }

    private ElanInstanceManager elanInstanceManager() {
        if (elanInstanceManager == null) {
            elanInstanceManager = new ElanInstanceManager(dataBroker, managerService(), elanInterfaceManager(),
                    interfaceManager());
        }
        return elanInstanceManager;
    }

    private IdManagerService idManager() {
        if (idManager == null) {
            // TODO lock manager? @Deprecate IdManager, intro. IdManager(DataBroker db, LockManagerService lockManager)
            idManager = new IdManager(dataBroker);
        }
        return idManager;
    }

    @SuppressWarnings("deprecation")
    private IInterfaceManager interfaceManager() {
        if (interfaceManager == null) {
            interfaceManager = TestInterfaceManager.newInstance();
        }
        return interfaceManager;
    }

    public void start() {
        elanService();
    }

    @Override
    public void close() throws Exception {
        idManager.close();
    }
}
