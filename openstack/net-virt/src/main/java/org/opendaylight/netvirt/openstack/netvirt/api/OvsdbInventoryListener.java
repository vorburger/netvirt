/*
 * Copyright (c) 2015 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.openstack.netvirt.api;

import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.DataObject;

public interface OvsdbInventoryListener {
    enum OvsdbType {
        NODE,
        ROW,
        OPENVSWITCH,
        BRIDGE,
        CONTROLLER,
        PORT
    }
    void ovsdbUpdate(Node node, DataObject augmentationDataChanges, OvsdbType type, Action action);
    void triggerUpdates();
}
