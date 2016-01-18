 /*
 * Copyright (C) 2014, 2016 Red Hat, Inc.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Authors : Dave Tucker, Flavio Fernandes
 */
package org.opendaylight.ovsdb.openstack.netvirt.impl;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.opendaylight.neutron.spi.INeutronNetworkCRUD;
import org.opendaylight.neutron.spi.INeutronPortCRUD;
import org.opendaylight.neutron.spi.INeutronSubnetCRUD;
import org.opendaylight.neutron.spi.NeutronFloatingIP;
import org.opendaylight.neutron.spi.NeutronNetwork;
import org.opendaylight.neutron.spi.NeutronPort;
import org.opendaylight.neutron.spi.NeutronRouter;
import org.opendaylight.neutron.spi.NeutronRouter_Interface;
import org.opendaylight.neutron.spi.NeutronSecurityGroup;
import org.opendaylight.neutron.spi.NeutronSubnet;
import org.opendaylight.neutron.spi.Neutron_IPs;
import org.opendaylight.ovsdb.openstack.netvirt.AbstractEvent;
import org.opendaylight.ovsdb.openstack.netvirt.AbstractHandler;
import org.opendaylight.ovsdb.openstack.netvirt.ConfigInterface;
import org.opendaylight.ovsdb.openstack.netvirt.NeutronL3AdapterEvent;
import org.opendaylight.ovsdb.openstack.netvirt.api.Action;
import org.opendaylight.ovsdb.openstack.netvirt.api.ArpProvider;
import org.opendaylight.ovsdb.openstack.netvirt.api.ConfigurationService;
import org.opendaylight.ovsdb.openstack.netvirt.api.Constants;
import org.opendaylight.ovsdb.openstack.netvirt.api.EventDispatcher;
import org.opendaylight.ovsdb.openstack.netvirt.api.GatewayMacResolver;
import org.opendaylight.ovsdb.openstack.netvirt.api.GatewayMacResolverListener;
import org.opendaylight.ovsdb.openstack.netvirt.api.IcmpEchoProvider;
import org.opendaylight.ovsdb.openstack.netvirt.api.InboundNatProvider;
import org.opendaylight.ovsdb.openstack.netvirt.api.L3ForwardingProvider;
import org.opendaylight.ovsdb.openstack.netvirt.api.NodeCacheManager;
import org.opendaylight.ovsdb.openstack.netvirt.api.OutboundNatProvider;
import org.opendaylight.ovsdb.openstack.netvirt.api.RoutingProvider;
import org.opendaylight.ovsdb.openstack.netvirt.api.SecurityServicesManager;
import org.opendaylight.ovsdb.openstack.netvirt.api.Southbound;
import org.opendaylight.ovsdb.openstack.netvirt.api.Status;
import org.opendaylight.ovsdb.openstack.netvirt.api.StatusCode;
import org.opendaylight.ovsdb.openstack.netvirt.api.TenantNetworkManager;
import org.opendaylight.ovsdb.utils.servicehelper.ServiceHelper;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev100924.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbTerminationPointAugmentation;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Neutron L3 Adapter implements a hub-like adapter for the various Neutron events. Based on
 * these events, the abstract router callbacks can be generated to the multi-tenant aware router,
 * as well as the multi-tenant router forwarding provider.
 */
public class NeutronL3Adapter extends AbstractHandler implements GatewayMacResolverListener, ConfigInterface {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeutronL3Adapter.class);

    // The implementation for each of these services is resolved by the OSGi Service Manager
    private volatile ConfigurationService configurationService;
    private volatile TenantNetworkManager tenantNetworkManager;
    private volatile NodeCacheManager nodeCacheManager;
    private volatile INeutronNetworkCRUD neutronNetworkCache;
    private volatile INeutronSubnetCRUD neutronSubnetCache;
    private volatile INeutronPortCRUD neutronPortCache;
    private volatile L3ForwardingProvider l3ForwardingProvider;
    private volatile InboundNatProvider inboundNatProvider;
    private volatile OutboundNatProvider outboundNatProvider;
    private volatile ArpProvider arpProvider;
    private volatile RoutingProvider routingProvider;
    private volatile GatewayMacResolver gatewayMacResolver;
    private volatile SecurityServicesManager securityServicesManager;
    private volatile IcmpEchoProvider icmpEchoProvider;

    private class FloatIpData {
        private final Long dpid;          // br-int of node where floating ip is associated with tenant port
        private final Long ofPort;        // patch port in br-int used to reach br-ex
        private final String segId;       // segmentation id of the net where fixed ip is instantiated
        private final String macAddress;  // mac address assigned to neutron port of floating ip
        private final String floatingIpAddress;
        private final String fixedIpAddress;  // ip address given to tenant vm
        private final String neutronRouterMac;

        FloatIpData(final Long dpid, final Long ofPort, final String segId, final String macAddress,
                    final String floatingIpAddress, final String fixedIpAddress, final String neutronRouterMac) {
            this.dpid = dpid;
            this.ofPort = ofPort;
            this.segId = segId;
            this.macAddress = macAddress;
            this.floatingIpAddress = floatingIpAddress;
            this.fixedIpAddress = fixedIpAddress;
            this.neutronRouterMac = neutronRouterMac;
        }
    }

    private Set<String> inboundIpRewriteCache;
    private Set<String> outboundIpRewriteCache;
    private Set<String> outboundIpRewriteExclusionCache;
    private Set<String> routerInterfacesCache;
    private Set<String> staticArpEntryCache;
    private Set<String> l3ForwardingCache;
    private Map<String, String> networkIdToRouterMacCache;
    private Map<String, List<Neutron_IPs>> networkIdToRouterIpListCache;
    private Map<String, NeutronRouter_Interface> subnetIdToRouterInterfaceCache;
    private Map<String, Pair<Long, Uuid>> neutronPortToDpIdCache;
    private Map<String, FloatIpData> floatIpDataMapCache;
    private String externalRouterMac;
    private Boolean enabled = false;
    private Southbound southbound;
    private Set<NeutronPort> portCleanupCache;

    private static final String OWNER_ROUTER_INTERFACE = "network:router_interface";
    private static final String OWNER_ROUTER_INTERFACE_DISTRIBUTED = "network:router_interface_distributed";
    private static final String OWNER_ROUTER_GATEWAY = "network:router_gateway";
    private static final String OWNER_FLOATING_IP = "network:floatingip";
    private static final String DEFAULT_EXT_RTR_MAC = "00:00:5E:00:01:01";

    public NeutronL3Adapter() {
        LOGGER.info(">>>>>> NeutronL3Adapter constructor {}", this.getClass());
    }

    private void initL3AdapterMembers() {
        Preconditions.checkNotNull(configurationService);

        if (configurationService.isL3ForwardingEnabled()) {
            this.inboundIpRewriteCache = new HashSet<>();
            this.outboundIpRewriteCache = new HashSet<>();
            this.outboundIpRewriteExclusionCache = new HashSet<>();
            this.routerInterfacesCache = new HashSet<>();
            this.staticArpEntryCache = new HashSet<>();
            this.l3ForwardingCache = new HashSet<>();
            this.networkIdToRouterMacCache = new HashMap<>();
            this.networkIdToRouterIpListCache = new HashMap<>();
            this.subnetIdToRouterInterfaceCache = new HashMap<>();
            this.neutronPortToDpIdCache = new HashMap<>();
            this.floatIpDataMapCache = new HashMap<>();

            this.externalRouterMac = configurationService.getDefaultGatewayMacAddress(null);
            if (this.externalRouterMac == null) {
                this.externalRouterMac = DEFAULT_EXT_RTR_MAC;
            }

            this.enabled = true;
            LOGGER.info("OVSDB L3 forwarding is enabled");
        } else {
            LOGGER.debug("OVSDB L3 forwarding is disabled");
        }
        this.portCleanupCache = new HashSet<>();
    }

    //
    // Callbacks from AbstractHandler
    //
    @Override
    public void processEvent(AbstractEvent abstractEvent) {
        if (!(abstractEvent instanceof NeutronL3AdapterEvent)) {
            LOGGER.error("Unable to process abstract event " + abstractEvent);
            return;
        }
        if (!this.enabled) {
            return;
        }

        NeutronL3AdapterEvent ev = (NeutronL3AdapterEvent) abstractEvent;
        switch (ev.getAction()) {
            case UPDATE:
                if (ev.getSubType() == NeutronL3AdapterEvent.SubType.SUBTYPE_EXTERNAL_MAC_UPDATE) {
                    updateExternalRouterMac( ev.getMacAddress().getValue() );
                } else {
                    LOGGER.warn("Received update for an unexpected event " + ev);
                }
                break;
            case ADD:
                // fall through...
                // break;
            case DELETE:
                // fall through...
                // break;
            default:
                LOGGER.warn("Unable to process event " + ev);
                break;
        }
    }

    //
    // Callbacks from GatewayMacResolverListener
    //

    @Override
    public void gatewayMacResolved(Long externalNetworkBridgeDpid, IpAddress gatewayIpAddress, MacAddress macAddress) {
        LOGGER.info("got gatewayMacResolved callback for ip {} on dpid {} to mac {}",
                gatewayIpAddress, externalNetworkBridgeDpid, macAddress);
        if (!this.enabled) {
            return;
        }

        if (macAddress == null || macAddress.getValue() == null) {
            // TODO: handle cases when mac is null
            return;
        }

        //
        // Enqueue event so update is handled by adapter's thread
        //
        enqueueEvent( new NeutronL3AdapterEvent(externalNetworkBridgeDpid, gatewayIpAddress, macAddress) );
    }

    //
    // Callbacks from OVSDB's northbound handlers
    //

    public void updateExternalRouterMac(final String externalRouterMacUpdate) {
        Preconditions.checkNotNull(externalRouterMacUpdate);

        flushExistingIpRewrite();
        this.externalRouterMac = externalRouterMacUpdate;
        rebuildExistingIpRewrite();
    }

    public void handleNeutronSubnetEvent(final NeutronSubnet subnet, Action action) {
        LOGGER.debug("Neutron subnet {} event : {}", action, subnet.toString());
        if (!this.enabled) {
            return;
        }
    }

    public void handleNeutronPortEvent(final NeutronPort neutronPort, NeutronPort originalPort, Action action) {
        LOGGER.debug("Neutron port {} event : {}", action, neutronPort.toString());

        if(action == Action.UPDATE){
            // FIXME: Bug 4971 Move cleanup cache to SG Impl
            this.processSecurityGroupUpdate(neutronPort,originalPort);
            this.updatePortInCleanupCache(neutronPort, originalPort);
        }

        if (!this.enabled) {
            return;
        }

        final boolean isDelete = action == Action.DELETE;

        if (neutronPort.getDeviceOwner().equalsIgnoreCase(OWNER_ROUTER_GATEWAY)) {
            if (!isDelete) {
                LOGGER.info("Port {} is network router gateway interface, "
                        + "triggering gateway resolution for the attached external network", neutronPort);
                this.triggerGatewayMacResolver(neutronPort);
            } else {
                NeutronNetwork externalNetwork = neutronNetworkCache.getNetwork(neutronPort.getNetworkUUID());
                if (externalNetwork != null && externalNetwork.isRouterExternal()){
                    final NeutronSubnet externalSubnet = getExternalNetworkSubnet(neutronPort);
                    // TODO support IPv6
                    if (externalSubnet != null && externalSubnet.getIpVersion() == 4) {
                        gatewayMacResolver.stopPeriodicRefresh(new Ipv4Address(externalSubnet.getGatewayIP()));
                    }
                }
            }
        }

        // Treat the port event as a router interface event if the port belongs to router. This is a
        // helper for handling cases when handleNeutronRouterInterfaceEvent is not available
        //
        if (neutronPort.getDeviceOwner().equalsIgnoreCase(OWNER_ROUTER_INTERFACE) ||
            neutronPort.getDeviceOwner().equalsIgnoreCase(OWNER_ROUTER_INTERFACE_DISTRIBUTED)) {

            if (neutronPort.getFixedIPs() != null) {
                for (Neutron_IPs neutronIP : neutronPort.getFixedIPs()) {
                    NeutronRouter_Interface neutronRouterInterface =
                        new NeutronRouter_Interface(neutronIP.getSubnetUUID(), neutronPort.getPortUUID());
                neutronRouterInterface.setID(neutronIP.getSubnetUUID());  // id of router interface to be same as subnet
                neutronRouterInterface.setTenantID(neutronPort.getTenantID());

                    this.handleNeutronRouterInterfaceEvent(null /*neutronRouter*/, neutronRouterInterface, action);
                }
            }
        } else {
            // We made it here, port is not used as a router interface. If this is not a delete action, make sure that
            // all nodes that are supposed to have a router interface for the port's subnet(s), have it configured. We
            // need to do this check here because a router interface is not added to a node until tenant becomes needed
            // there.
            //
            if (!isDelete) {
                if (neutronPort.getFixedIPs() != null) {
                    for (Neutron_IPs neutronIP : neutronPort.getFixedIPs()) {
                        NeutronRouter_Interface neutronRouterInterface =
                            subnetIdToRouterInterfaceCache.get(neutronIP.getSubnetUUID());
                        if (neutronRouterInterface != null) {
                            this.handleNeutronRouterInterfaceEvent(null /*neutronRouter*/, neutronRouterInterface, action);
                        }
                    }
                }
            }
            this.updateL3ForNeutronPort(neutronPort, isDelete);
        }
    }

    public void handleNeutronRouterEvent(final NeutronRouter neutronRouter, Action action) {
        LOGGER.debug("Neutron router {} event : {}", action, neutronRouter.toString());
        if (!this.enabled) {
            return;
        }
    }

    public void handleNeutronRouterInterfaceEvent(final NeutronRouter neutronRouter,
                                                  final NeutronRouter_Interface neutronRouterInterface,
                                                  Action action) {
        LOGGER.debug("Router interface {} got event {}. Subnet {}",
                     neutronRouterInterface.getPortUUID(),
                     action,
                     neutronRouterInterface.getSubnetUUID());
        if (!this.enabled) {
            return;
        }

        final boolean isDelete = action == Action.DELETE;

        this.programFlowsForNeutronRouterInterface(neutronRouterInterface, isDelete);

        // As neutron router interface is added/removed, we need to iterate through all the neutron ports and
        // see if they are affected by l3
        //
        for (NeutronPort neutronPort : neutronPortCache.getAllPorts()) {
            boolean currPortShouldBeDeleted = false;
            // Note: delete in this case only applies to 1)router interface delete and 2)ports on the same subnet
            if (isDelete) {
                if (neutronPort.getFixedIPs() != null) {
                    for (Neutron_IPs neutronIP : neutronPort.getFixedIPs()) {
                        if (neutronRouterInterface.getSubnetUUID().equalsIgnoreCase(neutronIP.getSubnetUUID())) {
                            currPortShouldBeDeleted = true;
                            break;
                        }
                    }
                }
            }
            this.updateL3ForNeutronPort(neutronPort, currPortShouldBeDeleted);
        }

        if (isDelete) {
            /*
             *  Bug 4277: Remove the router interface cache only after deleting the neutron port l3 flows.
             */
            this.cleanupRouterCache(neutronRouterInterface);
        }
    }

    public void handleNeutronFloatingIPEvent(final NeutronFloatingIP neutronFloatingIP,
                                             Action actionIn) {
        Preconditions.checkNotNull(neutronFloatingIP);

        LOGGER.debug(" Floating IP {} {}<->{}, network uuid {}", actionIn,
                neutronFloatingIP.getFixedIPAddress(),
                neutronFloatingIP.getFloatingIPAddress(),
                neutronFloatingIP.getFloatingNetworkUUID());
        if (!this.enabled) {
            return;
        }

        Action action;

        // Consider action to be delete if getFixedIPAddress is null
        //
        if (neutronFloatingIP.getFixedIPAddress() == null) {
            action = Action.DELETE;
        } else {
            action = actionIn;
        }

        // this.programFlowsForFloatingIP(neutronFloatingIP, action == Action.DELETE);

        if (action != Action.DELETE) {
            programFlowsForFloatingIPArpAdd(neutronFloatingIP);  // must be first, as it updates floatIpDataMapCache

            programFlowsForFloatingIPInbound(neutronFloatingIP, Action.ADD);
            programFlowsForFloatingIPOutbound(neutronFloatingIP, Action.ADD);
        } else {
            programFlowsForFloatingIPOutbound(neutronFloatingIP, Action.DELETE);
            programFlowsForFloatingIPInbound(neutronFloatingIP, Action.DELETE);

            programFlowsForFloatingIPArpDelete(neutronFloatingIP.getID()); // must be last, as it updates floatIpDataMapCache
        }
    }

    private void programFlowsForFloatingIPInbound(final NeutronFloatingIP neutronFloatingIP, final Action action) {
        Preconditions.checkNotNull(neutronFloatingIP);

        final FloatIpData fid = floatIpDataMapCache.get(neutronFloatingIP.getID());
        if (fid == null) {
            LOGGER.trace("programFlowsForFloatingIPInboundAdd {} for {} uuid {} not in local cache",
                    action, neutronFloatingIP.getFloatingIPAddress(), neutronFloatingIP.getID());
            return;
        }
        programInboundIpRewriteStage1(fid.dpid, fid.ofPort, fid.segId, fid.floatingIpAddress, fid.fixedIpAddress,
                                      action);
    }

    private void programFlowsForFloatingIPOutbound(final NeutronFloatingIP neutronFloatingIP, final Action action) {
        Preconditions.checkNotNull(neutronFloatingIP);

        final FloatIpData fid = floatIpDataMapCache.get(neutronFloatingIP.getID());
        if (fid == null) {
            LOGGER.trace("programFlowsForFloatingIPOutbound {} for {} uuid {} not in local cache",
                    action, neutronFloatingIP.getFloatingIPAddress(), neutronFloatingIP.getID());
            return;
        }
        programOutboundIpRewriteStage1(fid, action);
    }

    private void flushExistingIpRewrite() {
        for (FloatIpData fid : floatIpDataMapCache.values()) {
            programOutboundIpRewriteStage1(fid, Action.DELETE);
        }
    }

    private void rebuildExistingIpRewrite() {
        for (FloatIpData fid : floatIpDataMapCache.values()) {
            programOutboundIpRewriteStage1(fid, Action.ADD);
        }
    }

    private void programFlowsForFloatingIPArpAdd(final NeutronFloatingIP neutronFloatingIP) {
        Preconditions.checkNotNull(neutronFloatingIP);
        Preconditions.checkNotNull(neutronFloatingIP.getFixedIPAddress());
        Preconditions.checkNotNull(neutronFloatingIP.getFloatingIPAddress());

        if (floatIpDataMapCache.get(neutronFloatingIP.getID()) != null) {
            LOGGER.trace("programFlowsForFloatingIPArpAdd for neutronFloatingIP {} uuid {} is already done",
                    neutronFloatingIP.getFloatingIPAddress(), neutronFloatingIP.getID());
            return;
        }

        // find bridge Node where floating ip is configured by looking up cache for its port
        final NeutronPort neutronPortForFloatIp = findNeutronPortForFloatingIp(neutronFloatingIP.getID());
        final String neutronTenantPortUuid = neutronFloatingIP.getPortUUID();
        final Pair<Long, Uuid> nodeIfPair = neutronPortToDpIdCache.get(neutronTenantPortUuid);
        final String floatingIpMac = neutronPortForFloatIp == null ? null : neutronPortForFloatIp.getMacAddress();
        final String fixedIpAddress = neutronFloatingIP.getFixedIPAddress();
        final String floatingIpAddress = neutronFloatingIP.getFloatingIPAddress();

        final NeutronPort tenantNeutronPort = neutronPortCache.getPort(neutronTenantPortUuid);
        final NeutronNetwork tenantNeutronNetwork = tenantNeutronPort != null ?
                neutronNetworkCache.getNetwork(tenantNeutronPort.getNetworkUUID()) : null;
        final String providerSegmentationId = tenantNeutronNetwork != null ?
                tenantNeutronNetwork.getProviderSegmentationID() : null;
        final String neutronRouterMac = tenantNeutronNetwork != null ?
                networkIdToRouterMacCache.get(tenantNeutronNetwork.getID()) : null;

        if (nodeIfPair == null || neutronTenantPortUuid == null ||
                providerSegmentationId == null || providerSegmentationId.isEmpty() ||
                floatingIpMac == null || floatingIpMac.isEmpty() ||
                neutronRouterMac == null || neutronRouterMac.isEmpty()) {
            LOGGER.trace("Floating IP {}<->{}, incomplete floatPort {} tenantPortUuid {} seg {} mac {} rtrMac {}",
                    fixedIpAddress,
                    floatingIpAddress,
                    neutronPortForFloatIp,
                    neutronTenantPortUuid,
                    providerSegmentationId,
                    floatingIpMac,
                    neutronRouterMac);
            return;
        }

        // get ofport for patch port in br-int
        final Long dpId = nodeIfPair.getLeft();
        final Long ofPort = findOFPortForExtPatch(dpId);
        if (ofPort == null) {
            LOGGER.warn("Unable to locate OF port of patch port to connect floating ip to external bridge. dpid {}",
                    dpId);
            return;
        }

        // Respond to arps for the floating ip address via the patch port that connects br-int to br-ex
        //
        if (programStaticArpStage1(dpId, encodeExcplicitOFPort(ofPort), floatingIpMac, floatingIpAddress,
                Action.ADD)) {
            final FloatIpData floatIpData = new FloatIpData(dpId, ofPort, providerSegmentationId, floatingIpMac,
                    floatingIpAddress, fixedIpAddress, neutronRouterMac);
            floatIpDataMapCache.put(neutronFloatingIP.getID(), floatIpData);
            LOGGER.info("Floating IP {}<->{} programmed ARP mac {} on OFport {} seg {} dpid {}",
                    neutronFloatingIP.getFixedIPAddress(), neutronFloatingIP.getFloatingIPAddress(),
                    floatingIpMac, ofPort, providerSegmentationId, dpId);
        }
    }

    private void programFlowsForFloatingIPArpDelete(final String neutronFloatingIPUuid) {
        final FloatIpData floatIpData = floatIpDataMapCache.get(neutronFloatingIPUuid);
        if (floatIpData == null) {
            LOGGER.trace("programFlowsForFloatingIPArpDelete for uuid {} is not needed", neutronFloatingIPUuid);
            return;
        }

        if (programStaticArpStage1(floatIpData.dpid, encodeExcplicitOFPort(floatIpData.ofPort), floatIpData.macAddress,
                floatIpData.floatingIpAddress, Action.DELETE)) {
            floatIpDataMapCache.remove(neutronFloatingIPUuid);
            LOGGER.info("Floating IP {} un-programmed ARP mac {} on {} dpid {}",
                    floatIpData.floatingIpAddress, floatIpData.macAddress, floatIpData.ofPort, floatIpData.dpid);
        }
    }

    private final NeutronPort findNeutronPortForFloatingIp(final String floatingIpUuid) {
        for (NeutronPort neutronPort : neutronPortCache.getAllPorts()) {
            if (neutronPort.getDeviceOwner().equals(OWNER_FLOATING_IP) &&
                    neutronPort.getDeviceID().equals(floatingIpUuid)) {
                return neutronPort;
            }
        }
        return null;
    }

    private final Long findOFPortForExtPatch(Long dpId) {
        final String brInt = configurationService.getIntegrationBridgeName();
        final String brExt = configurationService.getExternalBridgeName();
        final String portNameInt = configurationService.getPatchPortName(new ImmutablePair<>(brInt, brExt));

        Preconditions.checkNotNull(dpId);
        Preconditions.checkNotNull(portNameInt);

        final long dpidPrimitive = dpId.longValue();
        for (Node node : nodeCacheManager.getBridgeNodes()) {
            if (dpidPrimitive == southbound.getDataPathId(node)) {
                final OvsdbTerminationPointAugmentation terminationPointOfBridge =
                        southbound.getTerminationPointOfBridge(node, portNameInt);
                return terminationPointOfBridge == null ? null : terminationPointOfBridge.getOfport();
            }
        }
        return null;
    }

    public void handleNeutronNetworkEvent(final NeutronNetwork neutronNetwork, Action action) {
        LOGGER.debug("neutronNetwork {}: network: {}", action, neutronNetwork);
        if (!this.enabled) {
            return;
        }
    }

    //
    // Callbacks from OVSDB's southbound handler
    //
    public void handleInterfaceEvent(final Node bridgeNode, final OvsdbTerminationPointAugmentation intf,
                                     final NeutronNetwork neutronNetwork, Action action) {
        LOGGER.debug("southbound interface {} node:{} interface:{}, neutronNetwork:{}",
                     action, bridgeNode.getNodeId().getValue(), intf.getName(), neutronNetwork);

        final NeutronPort neutronPort = tenantNetworkManager.getTenantPort(intf);
        if (action != Action.DELETE && neutronPort != null) {
            // FIXME: Bug 4971 Move cleanup cache to SG Impl
            storePortInCleanupCache(neutronPort);
        }

        if (!this.enabled) {
            return;
        }

        final Long dpId = getDpidForIntegrationBridge(bridgeNode);
        final Uuid interfaceUuid = intf.getInterfaceUuid();

        LOGGER.trace("southbound interface {} node:{} interface:{}, neutronNetwork:{} port:{} dpid:{} intfUuid:{}",
                action, bridgeNode.getNodeId().getValue(), intf.getName(), neutronNetwork, neutronPort, dpId, interfaceUuid);

        if (neutronPort != null) {
            final String neutronPortUuid = neutronPort.getPortUUID();

            if (action != Action.DELETE && neutronPortToDpIdCache.get(neutronPortUuid) == null &&
                    dpId != null && interfaceUuid != null) {
                handleInterfaceEventAdd(neutronPortUuid, dpId, interfaceUuid);
            }

            handleNeutronPortEvent(neutronPort, null, action);
        }

        if (action == Action.DELETE && interfaceUuid != null) {
            handleInterfaceEventDelete(intf, dpId);
        }
    }

    private void handleInterfaceEventAdd(final String neutronPortUuid, Long dpId, final Uuid interfaceUuid) {
        neutronPortToDpIdCache.put(neutronPortUuid, new ImmutablePair<>(dpId, interfaceUuid));
        LOGGER.debug("handleInterfaceEvent add cache entry NeutronPortUuid {} : dpid {}, ifUuid {}",
                neutronPortUuid, dpId, interfaceUuid.getValue());
    }

    private void handleInterfaceEventDelete(final OvsdbTerminationPointAugmentation intf, final Long dpId) {
        // Remove entry from neutronPortToDpIdCache based on interface uuid
        for (Map.Entry<String, Pair<Long, Uuid>> entry : neutronPortToDpIdCache.entrySet()) {
            final String currPortUuid = entry.getKey();
            if (intf.getInterfaceUuid().equals(entry.getValue().getRight())) {
                LOGGER.debug("handleInterfaceEventDelete remove cache entry NeutronPortUuid {} : dpid {}, ifUuid {}",
                        currPortUuid, dpId, intf.getInterfaceUuid().getValue());
                neutronPortToDpIdCache.remove(currPortUuid);
                break;
            }
        }
    }

    //
    // Internal helpers
    //
    private void updateL3ForNeutronPort(final NeutronPort neutronPort, final boolean isDelete) {

        final String networkUUID = neutronPort.getNetworkUUID();
        final String routerMacAddress = networkIdToRouterMacCache.get(networkUUID);

        // If there is no router interface handling the networkUUID, we are done
        if (routerMacAddress == null || routerMacAddress.isEmpty()) {
            return;
        }

        // If this is the neutron port for the router interface itself, ignore it as well. Ports that represent the
        // router interface are handled via handleNeutronRouterInterfaceEvent.
        if (routerMacAddress.equalsIgnoreCase(neutronPort.getMacAddress())) {
            return;
        }

        final NeutronNetwork neutronNetwork = neutronNetworkCache.getNetwork(networkUUID);
        final String providerSegmentationId = neutronNetwork != null ?
                                              neutronNetwork.getProviderSegmentationID() : null;
        final String tenantMac = neutronPort.getMacAddress();

        if (providerSegmentationId == null || providerSegmentationId.isEmpty() ||
            tenantMac == null || tenantMac.isEmpty()) {
            // done: go no further w/out all the info needed...
            return;
        }

        final Action action = isDelete ? Action.DELETE : Action.ADD;
        List<Node> nodes = nodeCacheManager.getBridgeNodes();
        if (nodes.isEmpty()) {
            LOGGER.trace("updateL3ForNeutronPort has no nodes to work with");
        }
        for (Node node : nodes) {
            final Long dpid = getDpidForIntegrationBridge(node);
            if (dpid == null) {
                continue;
            }
            if (neutronPort.getFixedIPs() == null) {
                continue;
            }
            for (Neutron_IPs neutronIP : neutronPort.getFixedIPs()) {
                final String tenantIpStr = neutronIP.getIpAddress();
                if (tenantIpStr.isEmpty()) {
                    continue;
                }

                // Configure L3 fwd. We do that regardless of tenant network present, because these rules are
                // still needed when routing to subnets non-local to node (bug 2076).
                programL3ForwardingStage1(node, dpid, providerSegmentationId, tenantMac, tenantIpStr, action);

                // Configure distributed ARP responder
                // Arp rule is only needed when segmentation exists in the given node (bug 4752).
                boolean arpNeeded = tenantNetworkManager.isTenantNetworkPresentInNode(node, providerSegmentationId);
                final Action actionForNode = arpNeeded ? action : Action.DELETE;
                programStaticArpStage1(dpid, providerSegmentationId, tenantMac, tenantIpStr, actionForNode);
            }
        }
    }

    private void processSecurityGroupUpdate(NeutronPort neutronPort,NeutronPort originalPort) {
        LOGGER.trace("processSecurityGroupUpdate:" + neutronPort);
        /**
         * Get updated data and original data for the the changed. Identify the security groups that got
         * added and removed and call the appropriate providers for updating the flows.
         */
        try {
            List<NeutronSecurityGroup> addedGroup = getsecurityGroupChanged(neutronPort,
                                                                            originalPort);
            List<NeutronSecurityGroup> deletedGroup = getsecurityGroupChanged(originalPort,
                                                                              neutronPort);

            if (null != addedGroup && !addedGroup.isEmpty()) {
                securityServicesManager.syncSecurityGroup(neutronPort,addedGroup,true);
            }
            if (null != deletedGroup && !deletedGroup.isEmpty()) {
                securityServicesManager.syncSecurityGroup(neutronPort,deletedGroup,false);
            }

        } catch (Exception e) {
            LOGGER.error("Exception in processSecurityGroupUpdate", e);
        }
    }

    private List<NeutronSecurityGroup> getsecurityGroupChanged(NeutronPort port1, NeutronPort port2) {
        LOGGER.trace("getsecurityGroupChanged:" + "Port1:" + port1 + "Port2" + port2);
        if (port1 == null) {
            return null;
        }
        ArrayList<NeutronSecurityGroup> list1 = new ArrayList<NeutronSecurityGroup>(port1.getSecurityGroups());
        if (port2 == null) {
            return list1;
        }
        ArrayList<NeutronSecurityGroup> list2 = new ArrayList<NeutronSecurityGroup>(port2.getSecurityGroups());
        for (Iterator<NeutronSecurityGroup> iterator = list1.iterator(); iterator.hasNext();) {
            NeutronSecurityGroup securityGroup1 = iterator.next();
            for (NeutronSecurityGroup securityGroup2 :list2) {
                if (securityGroup1.getSecurityGroupUUID().equals(securityGroup2.getSecurityGroupUUID())) {
                    iterator.remove();
                }
            }
        }
        return list1;
    }

    private void programL3ForwardingStage1(Node node, Long dpid, String providerSegmentationId,
                                           String macAddress, String ipStr,
                                           Action actionForNode) {
        // Based on the local cache, figure out whether programming needs to occur. To do this, we
        // will look at desired action for node.

        final String cacheKey = node.getNodeId().getValue() + ":" + providerSegmentationId + ":" + ipStr;
        final Boolean isProgrammed = l3ForwardingCache.contains(cacheKey);

        if (actionForNode == Action.DELETE && isProgrammed == Boolean.FALSE) {
            LOGGER.trace("programL3ForwardingStage1 for node {} providerId {} mac {} ip {} action {} is already done",
                         node.getNodeId().getValue(), providerSegmentationId, macAddress, ipStr, actionForNode);
            return;
        }
        if (actionForNode == Action.ADD && isProgrammed == Boolean.TRUE) {
            LOGGER.trace("programL3ForwardingStage1 for node {} providerId {} mac {} ip {} action {} is already done",
                    node.getNodeId().getValue(), providerSegmentationId, macAddress, ipStr, actionForNode);
            return;
        }

        Status status = this.programL3ForwardingStage2(node, dpid, providerSegmentationId,
                                                       macAddress, ipStr, actionForNode);
        if (status.isSuccess()) {
            // Update cache
            if (actionForNode == Action.ADD) {
                l3ForwardingCache.add(cacheKey);
            } else {
                l3ForwardingCache.remove(cacheKey);
            }
        }
    }

    private Status programL3ForwardingStage2(Node node, Long dpid, String providerSegmentationId,
                                             String macAddress,
                                             String address,
                                             Action actionForNode) {
        Status status;
        try {
            InetAddress inetAddress = InetAddress.getByName(address);
            status = l3ForwardingProvider == null ?
                     new Status(StatusCode.SUCCESS) :
                     l3ForwardingProvider.programForwardingTableEntry(dpid, providerSegmentationId,
                                                                      inetAddress, macAddress, actionForNode);
        } catch (UnknownHostException e) {
            status = new Status(StatusCode.BADREQUEST);
        }

        if (status.isSuccess()) {
            LOGGER.debug("ProgramL3Forwarding {} for mac:{} addr:{} node:{} action:{}",
                         l3ForwardingProvider == null ? "skipped" : "programmed",
                         macAddress, address, node.getNodeId().getValue(), actionForNode);
        } else {
            LOGGER.error("ProgramL3Forwarding failed for mac:{} addr:{} node:{} action:{} status:{}",
                         macAddress, address, node.getNodeId().getValue(), actionForNode, status);
        }
        return status;
    }

    // --

    private void programFlowsForNeutronRouterInterface(final NeutronRouter_Interface destNeutronRouterInterface,
                                                       Boolean isDelete) {
        Preconditions.checkNotNull(destNeutronRouterInterface);

        final NeutronPort neutronPort = neutronPortCache.getPort(destNeutronRouterInterface.getPortUUID());
        String macAddress = neutronPort != null ? neutronPort.getMacAddress() : null;
        List<Neutron_IPs> ipList = neutronPort != null ? neutronPort.getFixedIPs() : null;
        final NeutronSubnet subnet = neutronSubnetCache.getSubnet(destNeutronRouterInterface.getSubnetUUID());
        final NeutronNetwork neutronNetwork = subnet != null ?
                                              neutronNetworkCache.getNetwork(subnet.getNetworkUUID()) : null;
        final String destinationSegmentationId = neutronNetwork != null ?
                                                 neutronNetwork.getProviderSegmentationID() : null;
        final Boolean isExternal = neutronNetwork != null ? neutronNetwork.getRouterExternal() : Boolean.TRUE;
        final String cidr = subnet != null ? subnet.getCidr() : null;
        final int mask = getMaskLenFromCidr(cidr);

        LOGGER.trace("programFlowsForNeutronRouterInterface called for interface {} isDelete {}",
                     destNeutronRouterInterface, isDelete);

        // in delete path, mac address as well as ip address are not provided. Being so, let's find them from
        // the local cache
        if (neutronNetwork != null) {
            if (macAddress == null || macAddress.isEmpty()) {
                macAddress = networkIdToRouterMacCache.get(neutronNetwork.getNetworkUUID());
            }
            if (ipList == null || ipList.isEmpty()) {
                ipList = networkIdToRouterIpListCache.get(neutronNetwork.getNetworkUUID());
            }
        }

        if (destinationSegmentationId == null || destinationSegmentationId.isEmpty() ||
            cidr == null || cidr.isEmpty() ||
            macAddress == null || macAddress.isEmpty() ||
            ipList == null || ipList.isEmpty()) {
            LOGGER.debug("programFlowsForNeutronRouterInterface is bailing seg:{} cidr:{} mac:{}  ip:{}",
                         destinationSegmentationId, cidr, macAddress, ipList);
            // done: go no further w/out all the info needed...
            return;
        }

        final Action actionForNode = isDelete ? Action.DELETE : Action.ADD;

        // Keep cache for finding router's mac from network uuid -- add
        //
        if (! isDelete) {
            networkIdToRouterMacCache.put(neutronNetwork.getNetworkUUID(), macAddress);
            networkIdToRouterIpListCache.put(neutronNetwork.getNetworkUUID(), new ArrayList<>(ipList));
            subnetIdToRouterInterfaceCache.put(subnet.getSubnetUUID(), destNeutronRouterInterface);
        }

        List<Node> nodes = nodeCacheManager.getBridgeNodes();
        if (nodes.isEmpty()) {
            LOGGER.trace("programFlowsForNeutronRouterInterface has no nodes to work with");
        }
        for (Node node : nodes) {
            final Long dpid = getDpidForIntegrationBridge(node);
            if (dpid == null) {
                continue;
            }

            for (Neutron_IPs neutronIP : ipList) {
                final String ipStr = neutronIP.getIpAddress();
                if (ipStr.isEmpty()) {
                    LOGGER.debug("programFlowsForNeutronRouterInterface is skipping node {} ip {}",
                            node.getNodeId().getValue(), ipStr);
                    continue;
                }

                // Iterate through all other interfaces and add/remove reflexive flows to this interface
                //
                for (NeutronRouter_Interface srcNeutronRouterInterface : subnetIdToRouterInterfaceCache.values()) {
                    programFlowsForNeutronRouterInterfacePair(node, dpid,
                                                              srcNeutronRouterInterface, destNeutronRouterInterface,
                                                              neutronNetwork, destinationSegmentationId,
                                                              macAddress, ipStr, mask, actionForNode,
                                                              true /*isReflexsive*/);
                }

                if (! isExternal) {
                    programFlowForNetworkFromExternal(node, dpid, destinationSegmentationId, macAddress, ipStr, mask,
                            actionForNode);
                }
                programStaticArpStage1(dpid, destinationSegmentationId, macAddress, ipStr, actionForNode);
                programIcmpEcho(dpid, destinationSegmentationId, macAddress, ipStr, actionForNode);
            }

            // Compute action to be programmed. In the case of rewrite exclusions, we must never program rules
            // for the external neutron networks.
            //
            {
                final Action actionForRewriteExclusion = isExternal ? Action.DELETE : actionForNode;
                programIpRewriteExclusionStage1(node, dpid, destinationSegmentationId, cidr, actionForRewriteExclusion);
            }
        }

        // Keep cache for finding router's mac from network uuid -- NOTE: remove is done later, via cleanupRouterCache()
    }

    private void programFlowForNetworkFromExternal(final Node node,
                                                   final Long dpid,
                                                   final String destinationSegmentationId,
                                                   final String dstMacAddress,
                                                   final String destIpStr,
                                                   final int destMask,
                                                   final Action actionForNode) {
        programRouterInterfaceStage1(node, dpid, Constants.EXTERNAL_NETWORK, destinationSegmentationId,
                dstMacAddress, destIpStr, destMask, actionForNode);
    }

    private void programFlowsForNeutronRouterInterfacePair(final Node node,
                                                           final Long dpid,
                                                           final NeutronRouter_Interface srcNeutronRouterInterface,
                                                           final NeutronRouter_Interface dstNeutronRouterInterface,
                                                           final NeutronNetwork dstNeutronNetwork,
                                                           final String destinationSegmentationId,
                                                           final String dstMacAddress,
                                                           final String destIpStr,
                                                           final int destMask,
                                                           final Action actionForNode,
                                                           Boolean isReflexsive) {
        Preconditions.checkNotNull(srcNeutronRouterInterface);
        Preconditions.checkNotNull(dstNeutronRouterInterface);

        final String sourceSubnetId = srcNeutronRouterInterface.getSubnetUUID();
        if (sourceSubnetId == null) {
            LOGGER.error("Could not get provider Subnet ID from router interface {}",
                         srcNeutronRouterInterface.getID());
            return;
        }

        final NeutronSubnet sourceSubnet = neutronSubnetCache.getSubnet(sourceSubnetId);
        final String sourceNetworkId = sourceSubnet == null ? null : sourceSubnet.getNetworkUUID();
        if (sourceNetworkId == null) {
            LOGGER.error("Could not get provider Network ID from subnet {}", sourceSubnetId);
            return;
        }

        final NeutronNetwork sourceNetwork = neutronNetworkCache.getNetwork(sourceNetworkId);
        if (sourceNetwork == null) {
            LOGGER.error("Could not get provider Network for Network ID {}", sourceNetworkId);
            return;
        }

        if (! sourceNetwork.getTenantID().equals(dstNeutronNetwork.getTenantID())) {
            // Isolate subnets from different tenants within the same router
            return;
        }
        final String sourceSegmentationId = sourceNetwork.getProviderSegmentationID();
        if (sourceSegmentationId == null) {
            LOGGER.error("Could not get provider Segmentation ID for Subnet {}", sourceSubnetId);
            return;
        }
        if (sourceSegmentationId.equals(destinationSegmentationId)) {
            // Skip 'self'
            return;
        }

        programRouterInterfaceStage1(node, dpid, sourceSegmentationId, destinationSegmentationId,
                                     dstMacAddress, destIpStr, destMask, actionForNode);

        // Flip roles src->dst; dst->src
        if (isReflexsive) {
            final NeutronPort sourceNeutronPort = neutronPortCache.getPort(srcNeutronRouterInterface.getPortUUID());
            final String macAddress2 = sourceNeutronPort != null ? sourceNeutronPort.getMacAddress() : null;
            final List<Neutron_IPs> ipList2 = sourceNeutronPort != null ? sourceNeutronPort.getFixedIPs() : null;
            final String cidr2 = sourceSubnet.getCidr();
            final int mask2 = getMaskLenFromCidr(cidr2);

            if (cidr2 == null || cidr2.isEmpty() ||
                macAddress2 == null || macAddress2.isEmpty() ||
                ipList2 == null || ipList2.isEmpty()) {
                LOGGER.trace("programFlowsForNeutronRouterInterfacePair reflexive is bailing seg:{} cidr:{} mac:{} ip:{}",
                             sourceSegmentationId, cidr2, macAddress2, ipList2);
                // done: go no further w/out all the info needed...
                return;
            }

            for (Neutron_IPs neutronIP2 : ipList2) {
                final String ipStr2 = neutronIP2.getIpAddress();
                if (ipStr2.isEmpty()) {
                    continue;
                }
                programFlowsForNeutronRouterInterfacePair(node, dpid, dstNeutronRouterInterface,
                                                          srcNeutronRouterInterface,
                                                          sourceNetwork, sourceSegmentationId,
                                                          macAddress2, ipStr2, mask2, actionForNode,
                                                          false /*isReflexsive*/);
            }
        }
    }

    private void programRouterInterfaceStage1(Node node, Long dpid, String sourceSegmentationId,
                                              String destinationSegmentationId,
                                              String macAddress, String ipStr, int mask,
                                              Action actionForNode) {
        // Based on the local cache, figure out whether programming needs to occur. To do this, we
        // will look at desired action for node.
        //
        final String cacheKey = node.getNodeId().getValue() + ":" +
                                sourceSegmentationId + ":" + destinationSegmentationId + ":" +
                                ipStr + "/" + Integer.toString(mask);
        final Boolean isProgrammed = routerInterfacesCache.contains(cacheKey);

        if (actionForNode == Action.DELETE && isProgrammed == Boolean.FALSE) {
            LOGGER.trace("programRouterInterfaceStage1 for node {} sourceSegId {} destSegId {} mac {} ip {} mask {}" +
                         " action {} is already done",
                         node.getNodeId().getValue(), sourceSegmentationId, destinationSegmentationId,
                         macAddress, ipStr, mask, actionForNode);
        }
        if (actionForNode == Action.ADD && isProgrammed == Boolean.TRUE) {
            LOGGER.trace("programRouterInterfaceStage1 for node {} sourceSegId {} destSegId {} mac {} ip {} mask {}" +
                         " action {} is already done",
                         node.getNodeId().getValue(), sourceSegmentationId, destinationSegmentationId,
                         macAddress, ipStr, mask, actionForNode);
            return;
        }

        Status status = this.programRouterInterfaceStage2(node, dpid, sourceSegmentationId, destinationSegmentationId,
                                                          macAddress, ipStr, mask, actionForNode);
        if (status.isSuccess()) {
            // Update cache
            if (actionForNode == Action.ADD) {
                // TODO: multiTenantAwareRouter.addInterface(UUID.fromString(tenant), ...);
                routerInterfacesCache.add(cacheKey);
            } else {
                // TODO: multiTenantAwareRouter.removeInterface(...);
                routerInterfacesCache.remove(cacheKey);
            }
        }
    }

    private Status programRouterInterfaceStage2(Node node, Long dpid, String sourceSegmentationId,
                                                String destinationSegmentationId,
                                                String macAddress,
                                                String address, int mask,
                                                Action actionForNode) {
        Status status;
        try {
            InetAddress inetAddress = InetAddress.getByName(address);
            status = routingProvider == null ?
                     new Status(StatusCode.SUCCESS) :
                     routingProvider.programRouterInterface(dpid, sourceSegmentationId, destinationSegmentationId,
                                                            macAddress, inetAddress, mask, actionForNode);
        } catch (UnknownHostException e) {
            status = new Status(StatusCode.BADREQUEST);
        }

        if (status.isSuccess()) {
            LOGGER.debug("ProgramRouterInterface {} for mac:{} addr:{}/{} node:{} srcTunId:{} destTunId:{} action:{}",
                         routingProvider == null ? "skipped" : "programmed",
                         macAddress, address, mask, node.getNodeId().getValue(), sourceSegmentationId, destinationSegmentationId,
                         actionForNode);
        } else {
            LOGGER.error("ProgramRouterInterface failed for mac:{} addr:{}/{} node:{} srcTunId:{} destTunId:{} action:{} status:{}",
                         macAddress, address, mask, node.getNodeId().getValue(), sourceSegmentationId, destinationSegmentationId,
                         actionForNode, status);
        }
        return status;
    }

    private boolean programIcmpEcho(Long dpid, String segOrOfPort,
                                           String macAddress, String ipStr,
                                           Action action) {
        if (action == Action.DELETE ) {
            LOGGER.trace("Deleting Flow : programIcmpEcho dpid {} segOrOfPort {} mac {} ip {} action {}",
                    dpid, segOrOfPort, macAddress, ipStr, action);
        }
        if (action == Action.ADD) {
            LOGGER.trace("Adding Flow : programIcmpEcho dpid {} segOrOfPort {} mac {} ip {} action {}",
                    dpid, segOrOfPort, macAddress, ipStr, action);
        }

        Status status = new Status(StatusCode.UNSUPPORTED);
        if (icmpEchoProvider != null){
            try {
                InetAddress inetAddress = InetAddress.getByName(ipStr);
                status = icmpEchoProvider.programIcmpEchoEntry(dpid, segOrOfPort,
                                                macAddress, inetAddress, action);
            } catch (UnknownHostException e) {
                status = new Status(StatusCode.BADREQUEST);
            }
        }

        if (status.isSuccess()) {
            LOGGER.debug("programIcmpEcho {} for mac:{} addr:{} dpid:{} segOrOfPort:{} action:{}",
                    icmpEchoProvider == null ? "skipped" : "programmed",
                    macAddress, ipStr, dpid, segOrOfPort, action);
        } else {
            LOGGER.error("programIcmpEcho failed for mac:{} addr:{} dpid:{} segOrOfPort:{} action:{} status:{}",
                    macAddress, ipStr, dpid, segOrOfPort, action, status);
        }

        return status.isSuccess();
    }

    private boolean programStaticArpStage1(Long dpid, String segOrOfPort,
                                           String macAddress, String ipStr,
                                           Action action) {
        // Based on the local cache, figure out whether programming needs to occur. To do this, we
        // will look at desired action for node.
        //
        final String cacheKey = dpid + ":" + segOrOfPort + ":" + ipStr;
        final Boolean isProgrammed = staticArpEntryCache.contains(cacheKey);

        if (action == Action.DELETE && isProgrammed == Boolean.FALSE) {
            LOGGER.trace("programStaticArpStage1 dpid {} segOrOfPort {} mac {} ip {} action {} is already done",
                    dpid, segOrOfPort, macAddress, ipStr, action);
            return true;
        }
        if (action == Action.ADD && isProgrammed == Boolean.TRUE) {
            LOGGER.trace("programStaticArpStage1 dpid {} segOrOfPort {} mac {} ip {} action {} is already done",
                    dpid, segOrOfPort, macAddress, ipStr, action);
            return true;
        }

        Status status = this.programStaticArpStage2(dpid, segOrOfPort, macAddress, ipStr, action);
        if (status.isSuccess()) {
            // Update cache
            if (action == Action.ADD) {
                staticArpEntryCache.add(cacheKey);
            } else {
                staticArpEntryCache.remove(cacheKey);
            }
            return true;
        }
        return false;
    }

    private Status programStaticArpStage2(Long dpid,
                                          String segOrOfPort,
                                          String macAddress,
                                          String address,
                                          Action action) {
        Status status;
        try {
            InetAddress inetAddress = InetAddress.getByName(address);
            status = arpProvider == null ?
                     new Status(StatusCode.SUCCESS) :
                     arpProvider.programStaticArpEntry(dpid, segOrOfPort,
                                                       macAddress, inetAddress, action);
        } catch (UnknownHostException e) {
            status = new Status(StatusCode.BADREQUEST);
        }

        if (status.isSuccess()) {
            LOGGER.debug("ProgramStaticArp {} for mac:{} addr:{} dpid:{} segOrOfPort:{} action:{}",
                         arpProvider == null ? "skipped" : "programmed",
                         macAddress, address, dpid, segOrOfPort, action);
        } else {
            LOGGER.error("ProgramStaticArp failed for mac:{} addr:{} dpid:{} segOrOfPort:{} action:{} status:{}",
                         macAddress, address, dpid, segOrOfPort, action, status);
        }
        return status;
    }

    private boolean programInboundIpRewriteStage1(Long dpid, Long inboundOFPort, String providerSegmentationId,
                                                  String matchAddress, String rewriteAddress,
                                                  Action action) {
        // Based on the local cache, figure out whether programming needs to occur. To do this, we
        // will look at desired action for node.
        //
        final String cacheKey = dpid + ":" + inboundOFPort + ":" + providerSegmentationId + ":" + matchAddress;
        final Boolean isProgrammed = inboundIpRewriteCache.contains(cacheKey);

        if (action == Action.DELETE && isProgrammed == Boolean.FALSE) {
            LOGGER.trace("programInboundIpRewriteStage1 dpid {} OFPort {} seg {} matchAddress {} rewriteAddress {}" +
                    " action {} is already done",
                    dpid, inboundOFPort, providerSegmentationId, matchAddress, rewriteAddress, action);
            return true;
        }
        if (action == Action.ADD && isProgrammed == Boolean.TRUE) {
            LOGGER.trace("programInboundIpRewriteStage1 dpid {} OFPort {} seg {} matchAddress {} rewriteAddress {}" +
                    " action is already done",
                    dpid, inboundOFPort, providerSegmentationId, matchAddress, rewriteAddress, action);
            return true;
        }

        Status status = programInboundIpRewriteStage2(dpid, inboundOFPort, providerSegmentationId, matchAddress,
                rewriteAddress, action);
        if (status.isSuccess()) {
            // Update cache
            if (action == Action.ADD) {
                inboundIpRewriteCache.add(cacheKey);
            } else {
                inboundIpRewriteCache.remove(cacheKey);
            }
            return true;
        }
        return false;
    }

    private Status programInboundIpRewriteStage2(Long dpid, Long inboundOFPort, String providerSegmentationId,
                                                 String matchAddress, String rewriteAddress,
                                                 Action action) {
        Status status;
        try {
            InetAddress inetMatchAddress = InetAddress.getByName(matchAddress);
            InetAddress inetRewriteAddress = InetAddress.getByName(rewriteAddress);
            status = inboundNatProvider == null ?
                    new Status(StatusCode.SUCCESS) :
                    inboundNatProvider.programIpRewriteRule(dpid, inboundOFPort, providerSegmentationId,
                            inetMatchAddress, inetRewriteAddress,
                            action);
        } catch (UnknownHostException e) {
            status = new Status(StatusCode.BADREQUEST);
        }

        if (status.isSuccess()) {
            final boolean isSkipped = inboundNatProvider == null;
            LOGGER.debug("programInboundIpRewriteStage2 {} for dpid:{} ofPort:{} seg:{} match:{} rewrite:{} action:{}",
                    isSkipped ? "skipped" : "programmed",
                    dpid, inboundOFPort, providerSegmentationId, matchAddress, rewriteAddress, action);
        } else {
            LOGGER.error("programInboundIpRewriteStage2 failed for dpid:{} ofPort:{} seg:{} match:{} rewrite:{} action:{}" +
                         " status:{}",
                    dpid, inboundOFPort, providerSegmentationId, matchAddress, rewriteAddress, action,
                    status);
        }
        return status;
    }

    private void programIpRewriteExclusionStage1(Node node, Long dpid, String providerSegmentationId, String cidr,
                                                 Action actionForRewriteExclusion) {
        // Based on the local cache, figure out whether programming needs to occur. To do this, we
        // will look at desired action for node.
        //
        final String cacheKey = node.getNodeId().getValue() + ":" + providerSegmentationId + ":" + cidr;
        final Boolean isProgrammed = outboundIpRewriteExclusionCache.contains(cacheKey);

        if (actionForRewriteExclusion == Action.DELETE && isProgrammed == Boolean.FALSE) {
            LOGGER.trace("programIpRewriteExclusionStage1 node {} providerId {} cidr {} action {} is already done",
                         node.getNodeId().getValue(), providerSegmentationId, cidr, actionForRewriteExclusion);
            return;
        }
        if (actionForRewriteExclusion == Action.ADD && isProgrammed == Boolean.TRUE) {
            LOGGER.trace("programIpRewriteExclusionStage1 node {} providerId {} cidr {} action {} is already done",
                         node.getNodeId().getValue(), providerSegmentationId, cidr, actionForRewriteExclusion);
            return;
        }

        Status status = this.programIpRewriteExclusionStage2(node, dpid, providerSegmentationId, cidr,
                                                             actionForRewriteExclusion);
        if (status.isSuccess()) {
            // Update cache
            if (actionForRewriteExclusion == Action.ADD) {
                    outboundIpRewriteExclusionCache.add(cacheKey);
            } else {
                    outboundIpRewriteExclusionCache.remove(cacheKey);
            }
        }
    }

    private Status programIpRewriteExclusionStage2(Node node, Long dpid, String providerSegmentationId, String cidr,
                                                   Action actionForNode) {
        final Status status = outboundNatProvider == null ? new Status(StatusCode.SUCCESS) :
                outboundNatProvider.programIpRewriteExclusion(dpid, providerSegmentationId, cidr, actionForNode);

        if (status.isSuccess()) {
            final boolean isSkipped = outboundNatProvider == null;
            LOGGER.debug("IpRewriteExclusion {} for cidr:{} node:{} action:{}",
                         isSkipped ? "skipped" : "programmed",
                         cidr, node.getNodeId().getValue(), actionForNode);
        } else {
            LOGGER.error("IpRewriteExclusion failed for cidr:{} node:{} action:{} status:{}",
                         cidr, node.getNodeId().getValue(), actionForNode, status);
        }
        return status;
    }

    private void programOutboundIpRewriteStage1(FloatIpData fid, Action action) {
        // Based on the local cache, figure out whether programming needs to occur. To do this, we
        // will look at desired action for node.
        //
        final String cacheKey = fid.dpid + ":" + fid.segId + ":" + fid.fixedIpAddress;
        final Boolean isProgrammed = outboundIpRewriteCache.contains(cacheKey);

        if (action == Action.DELETE && isProgrammed == Boolean.FALSE) {
            LOGGER.trace("programOutboundIpRewriteStage1 dpid {} seg {} fixedIpAddress {} floatIp {} action {} " +
                         "is already done",
                    fid.dpid, fid.segId, fid.fixedIpAddress, fid.floatingIpAddress, action);
            return;
        }
        if (action == Action.ADD && isProgrammed == Boolean.TRUE) {
            LOGGER.trace("programOutboundIpRewriteStage1 dpid {} seg {} fixedIpAddress {} floatIp {} action {} " +
                         "is already done",
                    fid.dpid, fid.segId, fid.fixedIpAddress, fid.floatingIpAddress, action);
            return;
        }

        Status status = this.programOutboundIpRewriteStage2(fid, action);
        if (status.isSuccess()) {
            // Update cache
            if (action == Action.ADD) {
                outboundIpRewriteCache.add(cacheKey);
            } else {
                outboundIpRewriteCache.remove(cacheKey);
            }
        }
    }

    private Status programOutboundIpRewriteStage2(FloatIpData fid, Action action) {
        Status status;
        try {
            InetAddress matchSrcAddress = InetAddress.getByName(fid.fixedIpAddress);
            InetAddress rewriteSrcAddress = InetAddress.getByName(fid.floatingIpAddress);
            status = outboundNatProvider == null ?
                    new Status(StatusCode.SUCCESS) :
                    outboundNatProvider.programIpRewriteRule(
                            fid.dpid, fid.segId, fid.neutronRouterMac, matchSrcAddress, fid.macAddress,
                            this.externalRouterMac, rewriteSrcAddress, fid.ofPort, action);
        } catch (UnknownHostException e) {
            status = new Status(StatusCode.BADREQUEST);
        }

        if (status.isSuccess()) {
            final boolean isSkipped = outboundNatProvider == null;
            LOGGER.debug("programOutboundIpRewriteStage2 {} for dpid {} seg {} fixedIpAddress {} floatIp {}" +
                            " action {}",
                         isSkipped ? "skipped" : "programmed",
                         fid.dpid, fid.segId, fid.fixedIpAddress, fid.floatingIpAddress, action);
        } else {
            LOGGER.error("programOutboundIpRewriteStage2 failed for dpid {} seg {} fixedIpAddress {} floatIp {}" +
                         " action {} status:{}",
                         fid.dpid, fid.segId, fid.fixedIpAddress, fid.floatingIpAddress, action, status);
        }
        return status;
    }

    private int getMaskLenFromCidr(String cidr) {
        if (cidr == null) {
            return 0;
        }
        String[] splits = cidr.split("/");
        if (splits.length != 2) {
            return 0;
        }

        int result;
        try {
            result = Integer.parseInt(splits[1].trim());
        } catch (NumberFormatException nfe) {
            result = 0;
        }
        return result;
    }

    private Long getDpidForIntegrationBridge(Node node) {
        // Check if node is integration bridge; and only then return its dpid
        if (southbound.getBridge(node, configurationService.getIntegrationBridgeName()) != null) {
            return southbound.getDataPathId(node);
        }
        return null;
    }

    private Long getDpidForExternalBridge(Node node) {
        // Check if node is external bridge; and only then return its dpid
        if (southbound.getBridge(node, configurationService.getExternalBridgeName()) != null) {
            return southbound.getDataPathId(node);
        }
        return null;
    }

    private Node getExternalBridgeNode(){
        //Pickup the first node that has external bridge (br-ex).
        //NOTE: We are assuming that all the br-ex are serving one external network and gateway ip of
        //the external network is reachable from every br-ex
        // TODO: Consider other deployment scenario, and thing of better solution.
        List<Node> allBridges = nodeCacheManager.getBridgeNodes();
        for(Node node : allBridges){
            if (southbound.getBridge(node, configurationService.getExternalBridgeName()) != null) {
                return node;
            }
        }
        return null;
    }

    private NeutronSubnet getExternalNetworkSubnet(NeutronPort gatewayPort){
        if (gatewayPort.getFixedIPs() == null) {
            return null;
        }
        for (Neutron_IPs neutronIPs : gatewayPort.getFixedIPs()) {
            String subnetUUID = neutronIPs.getSubnetUUID();
            NeutronSubnet extSubnet = neutronSubnetCache.getSubnet(subnetUUID);
            if (extSubnet != null && extSubnet.getGatewayIP() != null) {
                return extSubnet;
            }
            if (extSubnet == null) {
                // TODO: when subnet is created, try again.
                LOGGER.debug("subnet {} in not found", subnetUUID);
            }
        }
        return null;
    }

     private void cleanupRouterCache(final NeutronRouter_Interface neutronRouterInterface) {
         /*
          *  Fix for 4277
          *  Remove the router cache only after deleting the neutron
          *  port l3 flows.
          */
         final NeutronPort neutronPort = neutronPortCache.getPort(neutronRouterInterface.getPortUUID());

         if (neutronPort != null) {
             networkIdToRouterMacCache.remove(neutronPort.getNetworkUUID());
             networkIdToRouterIpListCache.remove(neutronPort.getNetworkUUID());
             subnetIdToRouterInterfaceCache.remove(neutronRouterInterface.getSubnetUUID());
         }
     }

    private void triggerGatewayMacResolver(final NeutronPort gatewayPort){

        Preconditions.checkNotNull(gatewayPort);
        NeutronNetwork externalNetwork = neutronNetworkCache.getNetwork(gatewayPort.getNetworkUUID());

        if(externalNetwork != null){
            if(externalNetwork.isRouterExternal()){
                final NeutronSubnet externalSubnet = getExternalNetworkSubnet(gatewayPort);
                // TODO: address IPv6 case.
                if (externalSubnet != null &&
                    externalSubnet.getIpVersion() == 4 &&
                    gatewayPort.getFixedIPs() != null) {
                    if(externalSubnet.getGatewayIP() != null){
                        LOGGER.info("Trigger MAC resolution for gateway ip {}", externalSubnet.getGatewayIP());

                        gatewayMacResolver.resolveMacAddress(
                                this, /* gatewayMacResolverListener */
                                null, /* externalNetworkBridgeDpid */
                                true, /* refreshExternalNetworkBridgeDpidIfNeeded */
                                new Ipv4Address(externalSubnet.getGatewayIP()),
                                new Ipv4Address(gatewayPort.getFixedIPs().get(0).getIpAddress()),
                                new MacAddress(gatewayPort.getMacAddress()),
                                true /* periodicRefresh */);
                    }else{
                        LOGGER.warn("No gateway IP address found for external subnet {}",externalSubnet);
                    }
                }else{
                    LOGGER.warn("Neutron subnet not found for external network {}",externalNetwork);
                }
            }
        }else{
            LOGGER.warn("Neutron network not found for router interface {}",gatewayPort);
        }
    }


    private void storePortInCleanupCache(NeutronPort port) {
        this.portCleanupCache.add(port);
    }



    public void removePortFromCleanupCache(NeutronPort port) {
        this.portCleanupCache.remove(port);
    }


    private void updatePortInCleanupCache(NeutronPort updatedPort,NeutronPort originalPort) {
        removePortFromCleanupCache(originalPort);
        storePortInCleanupCache(updatedPort);
    }

    public NeutronPort getPortFromCleanupCache(String portid) {
        for (NeutronPort neutronPort : this.portCleanupCache) {
            if (neutronPort.getPortUUID() != null ) {
                if (neutronPort.getPortUUID().equals(portid)) {
                    LOGGER.info("getPortFromCleanupCache: Matching NeutronPort found {}", portid);
                    return neutronPort;
                    }
                }
            }
        return null;
    }

    /**
     * Return String that represents OF port with marker explicitly provided (reverse of MatchUtils:parseExplicitOFPort)
     *
     * @param ofPort the OF port number
     * @return the string with encoded OF port (example format "OFPort|999")
     */
    public static String encodeExcplicitOFPort(Long ofPort) {
        return "OFPort|" + ofPort.toString();
    }

    @Override
    public void setDependencies(BundleContext bundleContext, ServiceReference serviceReference) {
        eventDispatcher =
                (EventDispatcher) ServiceHelper.getGlobalInstance(EventDispatcher.class, this);
        eventDispatcher.eventHandlerAdded(
                bundleContext.getServiceReference(GatewayMacResolverListener.class.getName()), this);
        tenantNetworkManager =
                (TenantNetworkManager) ServiceHelper.getGlobalInstance(TenantNetworkManager.class, this);
        configurationService =
                (ConfigurationService) ServiceHelper.getGlobalInstance(ConfigurationService.class, this);
        arpProvider =
                (ArpProvider) ServiceHelper.getGlobalInstance(ArpProvider.class, this);
        inboundNatProvider =
                (InboundNatProvider) ServiceHelper.getGlobalInstance(InboundNatProvider.class, this);
        outboundNatProvider =
                (OutboundNatProvider) ServiceHelper.getGlobalInstance(OutboundNatProvider.class, this);
        routingProvider =
                (RoutingProvider) ServiceHelper.getGlobalInstance(RoutingProvider.class, this);
        l3ForwardingProvider =
                (L3ForwardingProvider) ServiceHelper.getGlobalInstance(L3ForwardingProvider.class, this);
        nodeCacheManager =
                (NodeCacheManager) ServiceHelper.getGlobalInstance(NodeCacheManager.class, this);
        southbound =
                (Southbound) ServiceHelper.getGlobalInstance(Southbound.class, this);
        gatewayMacResolver =
                (GatewayMacResolver) ServiceHelper.getGlobalInstance(GatewayMacResolver.class, this);
        securityServicesManager =
                (SecurityServicesManager) ServiceHelper.getGlobalInstance(SecurityServicesManager.class, this);

        initL3AdapterMembers();
    }

    @Override
    public void setDependencies(Object impl) {
        if (impl instanceof INeutronNetworkCRUD) {
            neutronNetworkCache = (INeutronNetworkCRUD)impl;
        } else if (impl instanceof INeutronPortCRUD) {
            neutronPortCache = (INeutronPortCRUD)impl;
        } else if (impl instanceof INeutronSubnetCRUD) {
            neutronSubnetCache = (INeutronSubnetCRUD)impl;
        } else if (impl instanceof ArpProvider) {
            arpProvider = (ArpProvider)impl;
        } else if (impl instanceof InboundNatProvider) {
            inboundNatProvider = (InboundNatProvider)impl;
        } else if (impl instanceof OutboundNatProvider) {
            outboundNatProvider = (OutboundNatProvider)impl;
        } else if (impl instanceof RoutingProvider) {
            routingProvider = (RoutingProvider)impl;
        } else if (impl instanceof L3ForwardingProvider) {
            l3ForwardingProvider = (L3ForwardingProvider)impl;
        }else if (impl instanceof GatewayMacResolver) {
            gatewayMacResolver = (GatewayMacResolver)impl;
        }else if (impl instanceof IcmpEchoProvider) {
            icmpEchoProvider = (IcmpEchoProvider)impl;
        }
    }
}
