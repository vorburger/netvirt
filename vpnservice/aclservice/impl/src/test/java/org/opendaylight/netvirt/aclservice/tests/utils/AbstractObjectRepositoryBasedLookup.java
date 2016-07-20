/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests.utils;

public abstract class AbstractObjectRepositoryBasedLookup {

    private ObjectRegistry objectRepository;

    public void setObjectRepository(ObjectRegistry objectRepository) {
        this.objectRepository = objectRepository;
    }

    protected ObjectRegistry getObjectRepository() {
        if (objectRepository == null) {
            throw new IllegalStateException("Must call setObjectRepository() first");
        }
        return objectRepository;
    }

}
