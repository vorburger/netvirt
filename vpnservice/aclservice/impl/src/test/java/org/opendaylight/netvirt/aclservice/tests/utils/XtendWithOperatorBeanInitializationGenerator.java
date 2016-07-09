/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests.utils;

import org.opendaylight.netvirt.aclservice.tests.FlowEntryObjects;

/**
 * Magic. pure Magic.
 *
 * @author Michael Vorburger
 */
public class XtendWithOperatorBeanInitializationGenerator {

    public void print(Object bean) {
        // ReflectExtensions
        new WithOperatorExpressionGenerator().printXtend(bean);
    }

    public static void main(String[] args) {
        new WithOperatorExpressionGenerator().printXtend(FlowEntryObjects.flow1());
    }
}
