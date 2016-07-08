/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests.utils;

import java.math.BigInteger;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.yangtools.concepts.Builder;

/**
 * Builder for FlowEntity.
 *
 * @author Michael Vorburger
 */
public class FlowEntityBuilder implements Builder<FlowEntity> {

    protected FlowEntity flowEntity = new FlowEntity(new BigInteger("9999999999999999"));

    @Override
    public FlowEntity build() {
        // TODO Auto-generated method stub
        return null;
    }



}
