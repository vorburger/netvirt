/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.cli;

import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.opendaylight.netvirt.elanmanager.api.IElanService;

@Command(scope = "elanInterface", name = "delete", description = "deleting Elan Interface")
public class ElanInterfaceDelete  extends OsgiCommandSupport {

    @Argument(index = 0, name = "elanName", description = "ELAN-NAME", required = true, multiValued = false)
    private String elanName;
    @Argument(index = 1, name = "interfaceName", description = "InterfaceName", required = true, multiValued = false)
    private String interfaceName;
    private static final Logger logger = LoggerFactory.getLogger(ElanInterfaceDelete.class);
    private IElanService elanProvider;

    public void setElanProvider(IElanService elanServiceProvider) {
        this.elanProvider = elanServiceProvider;
    }

    @Override
    protected Object doExecute() {
        try {
            logger.debug("Deleting ElanInterface command" + "\t" + elanName + "\t" + interfaceName + "\t");
            elanProvider.deleteElanInterface(elanName, interfaceName);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}