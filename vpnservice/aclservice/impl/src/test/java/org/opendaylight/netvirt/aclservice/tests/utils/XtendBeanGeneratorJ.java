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
 * Java wrapper around XtendBeanGenerator.
 *
 * Only needed because there is some bug ("Error: Could not find or load main
 * class org.opendaylight.netvirt.aclservice.tests.utils.XtendBeanGenerator")
 * which makes a main() in XtendBeanGenerator not work for some reason.
 *
 * @author Michael Vorburger
 */
public class XtendBeanGeneratorJ {

    public static void main(String[] args) {
        new XtendBeanGenerator().print(FlowEntryObjects.flow1());
    }
}
