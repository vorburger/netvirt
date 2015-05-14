/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice.interfacemgr.interfaces;

import java.util.List;
import org.opendaylight.vpnservice.mdsalutil.ActionInfo;
import org.opendaylight.vpnservice.mdsalutil.MatchInfo;

public interface IInterfaceManager {

    public Long getPortForInterface(String ifName);
    public long getDpnForInterface(String ifName);
    public String getEndpointIpForDpn(long dpnId);
    public List<MatchInfo> getInterfaceIngressRule(String ifName);
    public List<ActionInfo> getInterfaceEgressActions(String ifName);

}