/*
 * Copyright (c) 2016 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.it;

import java.util.UUID;

public class PortInfo {
    public String id;
    public String name;
    public String ip;
    public String mac;
    public long ofPort;
    public String macPfx = "f4:00:00:0f:00:";
    public String ipPfx = "10.0.0.";
    private NeutronPort neutronPort;
    protected int ovsInstance;

    PortInfo(int ovsInstance, long ofPort) {
        this.ovsInstance = ovsInstance;
        this.ofPort = ofPort;
        this.ip = ipFor(ofPort);
        this.mac = macFor(ofPort);
        this.id = UUID.randomUUID().toString();
        this.name = "tap" + id.substring(0, 11);
    }

    public void setNeutronPort(NeutronPort neutronPort) {
        this.neutronPort = neutronPort;
    }

    public NeutronPort getNeutronPort() {
        return neutronPort;
    }

    /**
     * Get the mac address for the n'th port created on this network (starts at 1).
     *
     * @param portNum index of port created
     * @return the mac address
     */
    public String macFor(long portNum) {
        return macPfx + String.format("%02x", portNum);
    }

    /**
     * Get the ip address for the n'th port created on this network (starts at 1).
     *
     * @param portNum index of port created
     * @return the mac address
     */
    public String ipFor(long portNum) {
        return ipPfx + portNum;
    }

    @Override
    public String toString() {
        return "PortInfo [name=" + name
                + ", ofPort=" + ofPort
                + ", id=" + id
                + ", mac=" + mac
                + ", ip=" + ip
                + "]";
    }
}