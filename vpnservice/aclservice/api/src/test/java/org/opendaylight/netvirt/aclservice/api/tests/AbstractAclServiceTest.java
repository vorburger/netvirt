/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.api.tests;

import org.opendaylight.controller.md.sal.binding.test.AbstractDataBrokerTest;

public abstract class AbstractAclServiceTest extends AbstractDataBrokerTest {

    // TODO AbstractDataBrokerTest should not have to be parent of this class,
    // but @Rule / @Inject in AclServiceImplTest ..
}
