/*
 * Copyright (C) 2014 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.openstack.netvirt.translator;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import java.io.Serializable;
import java.util.List;

@XmlRootElement
@XmlAccessorType(XmlAccessType.NONE)

public class NeutronLoadBalancerPoolMember
    implements Serializable, INeutronObject {

    private static final long serialVersionUID = 1L;

    /**
     * TODO: Plumb into LBaaS Pool. Members are nested underneath Pool CRUD.
     */
    @XmlElement (name = "id")
    String poolMemberID;

    @XmlElement (name = "tenant_id")
    String poolMemberTenantID;

    @XmlElement (name = "address")
    String poolMemberAddress;

    @XmlElement (name = "protocol_port")
    Integer poolMemberProtoPort;

    @XmlElement (name = "admin_state_up")
    Boolean poolMemberAdminStateIsUp;

    @XmlElement (name = "weight")
    Integer poolMemberWeight;

    @XmlElement (name = "subnet_id")
    String poolMemberSubnetID;

    String poolID;

    public NeutronLoadBalancerPoolMember() {
    }

    @XmlTransient
    public String getPoolID() {
        return poolID;
    }

    public void setPoolID(String poolID) {
        this.poolID = poolID;
    }

    public String getID() {
        return poolMemberID;
    }

    public void setID(String id) {
        poolMemberID = id;
    }

    // @deprecated use getID()
    public String getPoolMemberID() {
        return poolMemberID;
    }

    // @deprecated use setID()
    public void setPoolMemberID(String poolMemberID) {
        this.poolMemberID = poolMemberID;
    }

    public String getPoolMemberTenantID() {
        return poolMemberTenantID;
    }

    public void setPoolMemberTenantID(String poolMemberTenantID) {
        this.poolMemberTenantID = poolMemberTenantID;
    }

    public String getPoolMemberAddress() {
        return poolMemberAddress;
    }

    public void setPoolMemberAddress(String poolMemberAddress) {
        this.poolMemberAddress = poolMemberAddress;
    }

    public Integer getPoolMemberProtoPort() {
        return poolMemberProtoPort;
    }

    public void setPoolMemberProtoPort(Integer poolMemberProtoPort) {
        this.poolMemberProtoPort = poolMemberProtoPort;
    }

    public Boolean getPoolMemberAdminStateIsUp() {
        return poolMemberAdminStateIsUp;
    }

    public void setPoolMemberAdminStateIsUp(Boolean poolMemberAdminStateIsUp) {
        this.poolMemberAdminStateIsUp = poolMemberAdminStateIsUp;
    }

    public Integer getPoolMemberWeight() {
        return poolMemberWeight;
    }

    public void setPoolMemberWeight(Integer poolMemberWeight) {
        this.poolMemberWeight = poolMemberWeight;
    }

    public String getPoolMemberSubnetID() {
        return poolMemberSubnetID;
    }

    public void setPoolMemberSubnetID(String poolMemberSubnetID) {
        this.poolMemberSubnetID = poolMemberSubnetID;
    }

    public NeutronLoadBalancerPoolMember extractFields(List<String> fields) {
        NeutronLoadBalancerPoolMember ans = new NeutronLoadBalancerPoolMember();
        for (String s : fields) {
            switch (s) {
                case "id":
                    ans.setID(this.getID());
                    break;
                case "pool_id":
                    ans.setPoolID(this.getPoolID());
                    break;
                case "tenant_id":
                    ans.setPoolMemberTenantID(this.getPoolMemberTenantID());
                    break;
                case "address":
                    ans.setPoolMemberAddress(this.getPoolMemberAddress());
                    break;
                case "protocol_port":
                    ans.setPoolMemberProtoPort(this.getPoolMemberProtoPort());
                    break;
                case "admin_state_up":
                    ans.setPoolMemberAdminStateIsUp(poolMemberAdminStateIsUp);
                    break;
                case "weight":
                    ans.setPoolMemberWeight(this.getPoolMemberWeight());
                    break;
                case "subnet_id":
                    ans.setPoolMemberSubnetID(this.getPoolMemberSubnetID());
                    break;
            }
        }
        return ans;
    }
    @Override public String toString() {
        return "NeutronLoadBalancerPoolMember{" +
                "poolMemberID='" + poolMemberID + '\'' +
                ", poolID='" + poolID + '\'' +
                ", poolMemberTenantID='" + poolMemberTenantID + '\'' +
                ", poolMemberAddress='" + poolMemberAddress + '\'' +
                ", poolMemberProtoPort=" + poolMemberProtoPort +
                ", poolMemberAdminStateIsUp=" + poolMemberAdminStateIsUp +
                ", poolMemberWeight=" + poolMemberWeight +
                ", poolMemberSubnetID='" + poolMemberSubnetID + '\'' +
                '}';
    }
}
