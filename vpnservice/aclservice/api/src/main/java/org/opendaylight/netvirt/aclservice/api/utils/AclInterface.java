/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.api.utils;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairs;

/**
 * The Class AclInterface.
 */
// @NonNullByDefault({})
public class AclInterface {

    /** The port security enabled. */
    private @Nullable Boolean portSecurityEnabled;

    /** The interface id. */
    private @Nullable String interfaceId;

    /** The l port tag. */
    private @Nullable Integer lportTag;

    /** The dp id. */
    private @Nullable BigInteger dpId;

    /** The security groups. */
    private final List<Uuid> securityGroups = new ArrayList<>();

    /** The allowed address pairs. */
    private final List<AllowedAddressPairs> allowedAddressPairs = new ArrayList<>();

    /**
     * Checks if is port security enabled.
     *
     * @return the boolean
     */
    public Optional<Boolean> isPortSecurityEnabled() {
        return Optional.ofNullable(portSecurityEnabled);
    }

    /**
     * Gets the port security enabled.
     *
     * @return the port security enabled
     */
    public Optional<Boolean> getPortSecurityEnabled() {
        return Optional.ofNullable(portSecurityEnabled);
    }

    /**
     * Sets the port security enabled.
     *
     * @param portSecurityEnabled the new port security enabled
     */
    public void setPortSecurityEnabled(Boolean portSecurityEnabled) {
        this.portSecurityEnabled = portSecurityEnabled;
    }

    /**
     * Gets the interface id.
     *
     * @return the interface id
     */
    public Optional<String> getInterfaceId() {
        return Optional.ofNullable(interfaceId);
    }

    /**
     * Sets the interface id.
     *
     * @param interfaceId the new interface id
     */
    public void setInterfaceId(String interfaceId) {
        this.interfaceId = interfaceId;
    }

    /**
     * Gets the l port tag.
     *
     * @return the l port tag
     */
    public Optional<Integer> getLPortTag() {
        return Optional.ofNullable(lportTag);
    }

    /**
     * Sets the l port tag.
     *
     * @param lportTag the new l port tag
     */
    public void setLPortTag(Integer lportTag) {
        this.lportTag = lportTag;
    }

    /**
     * Gets the dp id.
     *
     * @return the dp id
     */
    public Optional<BigInteger> getDpId() {
        return Optional.ofNullable(dpId);
    }

    /**
     * Sets the dp id.
     *
     * @param dpId the new dp id
     */
    public void setDpId(BigInteger dpId) {
        this.dpId = dpId;
    }

    /**
     * Gets the security groups.
     *
     * @return the security groups
     */
    public List<Uuid> getSecurityGroups() {
        return securityGroups;
    }

    /**
     * Gets the allowed address pairs.
     *
     * @return the allowed address pairs
     */
    public List<AllowedAddressPairs> getAllowedAddressPairs() {
        return allowedAddressPairs;
    }

    @Override
    public int hashCode() {
        return Objects.hash(portSecurityEnabled, dpId, interfaceId, lportTag, securityGroups, allowedAddressPairs);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        return MoreObjects2.equalsHelper(this, obj, (one, another) ->
               Objects.equals(one.portSecurityEnabled, another.portSecurityEnabled)
            && Objects.equals(one.dpId, another.dpId)
            && Objects.equals(one.interfaceId, another.interfaceId)
            && Objects.equals(one.lportTag, another.lportTag)
            && Objects.equals(one.securityGroups, another.securityGroups)
            && Objects.equals(one.allowedAddressPairs, another.allowedAddressPairs)
        );
    }

    @Override
    public String toString() {
        return "AclInterface [portSecurityEnabled=" + portSecurityEnabled + ", interfaceId=" + interfaceId
                + ", lportTag=" + lportTag + ", dpId=" + dpId + ", securityGroups=" + securityGroups
                + ", allowedAddressPairs=" + allowedAddressPairs + "]";
    }
}
