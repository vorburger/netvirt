/*
 * Copyright (C) 2013 Ebay Software Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Authors : Ashwin Raveendran
 */
package org.opendaylight.ovsdb.lib.table.internal;

import java.util.ArrayList;
import java.util.List;

import org.opendaylight.ovsdb.lib.table.*;

public class Tables {
    public static List<Table> tables = new ArrayList<Table>();
    static {
        tables.add(new Bridge());
        tables.add(new Port());
        tables.add(new Capability());
        tables.add(new Interface());
        tables.add(new Controller());
        tables.add(new Manager());
        tables.add(new Mirror());
        tables.add(new NetFlow());
        tables.add(new Open_vSwitch());
        tables.add(new Qos());
        tables.add(new Queue());
        tables.add(new SFlow());
        tables.add(new SSL());
    }
    public static List<Table> getTables() {
        return tables;
    }
}
