/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice;

import org.opendaylight.genius.mdsalutil.FlowEntity;

/**
 * Installs and removes FlowEntities.
 *
 * @author Michael Vorburger
 */
public interface FlowEntityRepository {

    // TODO This class should eventually probably be moved somewhere else

    void installFlow(FlowEntity flow);

    void removeFlow(FlowEntity flow);

}
