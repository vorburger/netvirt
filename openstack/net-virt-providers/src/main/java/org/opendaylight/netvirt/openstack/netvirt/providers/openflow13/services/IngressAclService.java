/*
 * Copyright (c) 2014 - 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.openstack.netvirt.providers.openflow13.services;

import com.google.common.collect.Lists;

import org.opendaylight.netvirt.openstack.netvirt.providers.openflow13.AbstractServiceInstance;
import org.opendaylight.netvirt.openstack.netvirt.providers.openflow13.Service;
import org.opendaylight.netvirt.openstack.netvirt.api.Constants;
import org.opendaylight.netvirt.openstack.netvirt.api.IngressAclProvider;
import org.opendaylight.netvirt.openstack.netvirt.api.SecurityGroupCacheManger;
import org.opendaylight.netvirt.openstack.netvirt.api.SecurityServicesManager;
import org.opendaylight.netvirt.openstack.netvirt.providers.ConfigInterface;
import org.opendaylight.netvirt.openstack.netvirt.translator.NeutronSecurityGroup;
import org.opendaylight.netvirt.openstack.netvirt.translator.NeutronSecurityRule;
import org.opendaylight.netvirt.openstack.netvirt.translator.Neutron_IPs;
import org.opendaylight.netvirt.openstack.netvirt.translator.crud.INeutronSecurityRuleCRUD;
import org.opendaylight.netvirt.utils.mdsal.openflow.ActionUtils;
import org.opendaylight.netvirt.utils.mdsal.openflow.FlowUtils;
import org.opendaylight.netvirt.utils.mdsal.openflow.InstructionUtils;
import org.opendaylight.netvirt.utils.mdsal.openflow.MatchUtils;
import org.opendaylight.netvirt.utils.servicehelper.ServiceHelper;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Ipv6Prefix;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.InstructionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeBuilder;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class IngressAclService extends AbstractServiceInstance implements IngressAclProvider, ConfigInterface {
    private static final Logger LOG = LoggerFactory.getLogger(IngressAclService.class);
    private volatile SecurityServicesManager securityServicesManager;
    private volatile SecurityGroupCacheManger securityGroupCacheManger;
    private volatile INeutronSecurityRuleCRUD neutronSecurityRule;
    private static final int PORT_RANGE_MIN = 1;
    private static final int PORT_RANGE_MAX = 65535;

    public IngressAclService() {
        super(Service.INGRESS_ACL);
    }

    public IngressAclService(Service service) {
        super(service);
    }

    @Override
    public void programPortSecurityGroup(Long dpid, String segmentationId, String attachedMac,
                                       long localPort, NeutronSecurityGroup securityGroup,
                                       String portUuid, boolean write) {

        LOG.trace("programPortSecurityGroup neutronSecurityGroup: {} ", securityGroup);
        if (securityGroup == null || getSecurityRulesforGroup(securityGroup) == null) {
            return;
        }

        List<NeutronSecurityRule> portSecurityList = getSecurityRulesforGroup(securityGroup);
        /* Iterate over the Port Security Rules in the Port Security Group bound to the port*/
        for (NeutronSecurityRule portSecurityRule : portSecurityList) {

            /**
             * Neutron Port Security Acl "ingress" and "IPv4"
             * Check that the base conditions for flow based Port Security are true:
             * Port Security Rule Direction ("ingress") and Protocol ("IPv4")
             * Neutron defines the direction "ingress" as the vSwitch to the VM as defined in:
             * http://docs.openstack.org/api/openstack-network/2.0/content/security_groups.html
             *
             */

            if (portSecurityRule == null
                    || portSecurityRule.getSecurityRuleEthertype() == null
                    || portSecurityRule.getSecurityRuleDirection() == null) {
                continue;
            }

            if (NeutronSecurityRule.DIRECTION_INGRESS.equals(portSecurityRule.getSecurityRuleDirection())) {
                LOG.debug("programPortSecurityGroup: Rule matching IP and ingress is: {} ", portSecurityRule);
                if (null != portSecurityRule.getSecurityRemoteGroupID()) {
                    //Remote Security group is selected
                    List<Neutron_IPs> remoteSrcAddressList = securityServicesManager
                            .getVmListForSecurityGroup(portUuid,portSecurityRule.getSecurityRemoteGroupID());
                    if (null != remoteSrcAddressList) {
                        for (Neutron_IPs vmIp :remoteSrcAddressList ) {
                            programPortSecurityRule(dpid, segmentationId, attachedMac, localPort,
                                                    portSecurityRule, vmIp, write);
                        }
                        if (write) {
                            securityGroupCacheManger.addToCache(portSecurityRule.getSecurityRemoteGroupID(), portUuid);
                        } else {
                            securityGroupCacheManger.removeFromCache(portSecurityRule.getSecurityRemoteGroupID(),
                                                                     portUuid);
                        }
                    }
                } else {
                    programPortSecurityRule(dpid, segmentationId, attachedMac, localPort,
                                            portSecurityRule, null, write);
                }
                if (write) {
                    securityGroupCacheManger.portAdded(securityGroup.getSecurityGroupUUID(), portUuid);
                } else {
                    securityGroupCacheManger.portRemoved(securityGroup.getSecurityGroupUUID(), portUuid);
                }
            }
        }
    }

    @Override
    public void programPortSecurityRule(Long dpid, String segmentationId, String attachedMac,
                                        long localPort, NeutronSecurityRule portSecurityRule,
                                        Neutron_IPs vmIp, boolean write) {
        String securityRuleEtherType = portSecurityRule.getSecurityRuleEthertype();
        boolean isIpv6 = NeutronSecurityRule.ETHERTYPE_IPV6.equals(securityRuleEtherType);
        if (!isIpv6 && !NeutronSecurityRule.ETHERTYPE_IPV4.equals(securityRuleEtherType)) {
            LOG.debug("programPortSecurityRule: SecurityRuleEthertype {} does not match IPv4/v6.",
                      securityRuleEtherType);
            return;
        }

        if (null == portSecurityRule.getSecurityRuleProtocol()) {
            ingressAclIp(dpid, isIpv6, segmentationId, attachedMac,
                         write, Constants.PROTO_PORT_PREFIX_MATCH_PRIORITY);
        } else {
            String ipaddress = null;
            if (null != vmIp) {
                ipaddress = vmIp.getIpAddress();
                try {
                    InetAddress address = InetAddress.getByName(vmIp.getIpAddress());
                    if ((isIpv6 && (address instanceof Inet4Address)) || (!isIpv6 && address instanceof Inet6Address)) {
                        LOG.debug("programPortSecurityRule: Remote vmIP {} does not match "
                                + "with SecurityRuleEthertype {}.", ipaddress, securityRuleEtherType);
                        return;
                    }
                } catch (UnknownHostException e) {
                    LOG.warn("Invalid IP address {}", ipaddress, e);
                    return;
                }
            }

            switch (portSecurityRule.getSecurityRuleProtocol()) {
                case MatchUtils.TCP:
                    LOG.debug("programPortSecurityRule: Rule matching TCP", portSecurityRule);
                    ingressAclTcp(dpid, segmentationId, attachedMac, portSecurityRule, ipaddress,
                              write, Constants.PROTO_PORT_PREFIX_MATCH_PRIORITY);
                    break;
                case MatchUtils.UDP:
                    LOG.debug("programPortSecurityRule: Rule matching UDP", portSecurityRule);
                    ingressAclUdp(dpid, segmentationId, attachedMac, portSecurityRule, ipaddress,
                                write, Constants.PROTO_PORT_PREFIX_MATCH_PRIORITY);
                    break;
                case MatchUtils.ICMP:
                case MatchUtils.ICMPV6:
                    LOG.debug("programPortSecurityRule: Rule matching ICMP", portSecurityRule);
                    ingressAclIcmp(dpid, segmentationId, attachedMac, portSecurityRule, ipaddress,
                                 write, Constants.PROTO_PORT_PREFIX_MATCH_PRIORITY);
                    break;
                default:
                    LOG.info("programPortSecurityAcl: Protocol is not TCP/UDP/ICMP but other "
                            + "protocol = ", portSecurityRule.getSecurityRuleProtocol());
                    ingressOtherProtocolAclHandler(dpid, segmentationId, attachedMac, portSecurityRule,
                              null, write, Constants.PROTO_PORT_PREFIX_MATCH_PRIORITY);
                    break;
            }
        }
    }

    private void ingressOtherProtocolAclHandler(Long dpidLong, String segmentationId, String dstMac,
          NeutronSecurityRule portSecurityRule, String srcAddress,
          boolean write, Integer protoPortMatchPriority) {
        MatchBuilder matchBuilder = new MatchBuilder();
        String flowId = "Ingress_Other_" + segmentationId + "_" + dstMac + "_";
        matchBuilder = MatchUtils.createV4EtherMatchWithType(matchBuilder,null,dstMac,MatchUtils.ETHERTYPE_IPV4);
        short proto = 0;
        try {
            Integer protocol = new Integer(portSecurityRule.getSecurityRuleProtocol());
            proto = protocol.shortValue();
            flowId = flowId + proto;
        } catch (NumberFormatException e) {
            LOG.error("Protocol vlaue conversion failure", e);
        }
        matchBuilder = MatchUtils.createIpProtocolMatch(matchBuilder, proto);
        if (null != srcAddress) {
            flowId = flowId + srcAddress;
            matchBuilder = MatchUtils.addRemoteIpPrefix(matchBuilder,
                                                        MatchUtils.iPv4PrefixFromIPv4Address(srcAddress), null);
        } else if (null != portSecurityRule.getSecurityRuleRemoteIpPrefix()) {
            flowId = flowId + portSecurityRule.getSecurityRuleRemoteIpPrefix();
            matchBuilder = MatchUtils.addRemoteIpPrefix(matchBuilder,
                                           new Ipv4Prefix(portSecurityRule.getSecurityRuleRemoteIpPrefix()),null);
        }
        flowId = flowId + "_Permit";
        NodeBuilder nodeBuilder = FlowUtils.createNodeBuilder(dpidLong);
        FlowBuilder flowBuilder = FlowUtils.createFlowBuilder(flowId, protoPortMatchPriority,
                                                              matchBuilder, getTable());
        addInstructionWithConntrackCommit(flowBuilder, false);
        syncFlow(flowBuilder ,nodeBuilder, write);
    }

    @Override
    public void programFixedSecurityGroup(Long dpid, String segmentationId, String dhcpMacAddress,
                                        long localPort, String attachMac, boolean write) {

        ingressAclDhcpAllowServerTraffic(dpid, segmentationId,dhcpMacAddress, attachMac,
                                         write,Constants.PROTO_DHCP_SERVER_MATCH_PRIORITY);
        ingressAclDhcpv6AllowServerTraffic(dpid, segmentationId,dhcpMacAddress, attachMac,
                                           write,Constants.PROTO_DHCP_SERVER_MATCH_PRIORITY);

        if (securityServicesManager.isConntrackEnabled()) {
            programIngressAclFixedConntrackRule(dpid, segmentationId, attachMac, localPort, write);
        }
        programArpRule(dpid, segmentationId, localPort, attachMac, write);
    }

    private void programArpRule(Long dpid, String segmentationId, long localPort, String attachMac, boolean write) {
        MatchBuilder matchBuilder = new MatchBuilder();
        String flowId = "Ingress_ARP_" + segmentationId + "_" + localPort + "_";
        MatchUtils.createV4EtherMatchWithType(matchBuilder,null,null,MatchUtils.ETHERTYPE_ARP);
        MatchUtils.addArpMacMatch(matchBuilder, null, attachMac);
        FlowBuilder flowBuilder = FlowUtils.createFlowBuilder(flowId, Constants.PROTO_MATCH_PRIORITY,
                                                              matchBuilder, getTable());
        addPipelineInstruction(flowBuilder, null, false);
        NodeBuilder nodeBuilder = FlowUtils.createNodeBuilder(dpid);
        syncFlow(flowBuilder ,nodeBuilder, write);
    }

    private void programIngressAclFixedConntrackRule(Long dpid,
           String segmentationId, String attachMac, long localPort, boolean write) {
        try {
            String nodeName = Constants.OPENFLOW_NODE_PREFIX + dpid;
            programConntrackUntrackRule(dpid, segmentationId, localPort, attachMac,
                                        Constants.CT_STATE_UNTRACKED_PRIORITY, write );
            programConntrackTrackedPlusEstRule(dpid, segmentationId, localPort, attachMac,
                                        Constants.CT_STATE_TRACKED_EXIST_PRIORITY, write );
            programConntrackTrackedPlusRelRule(dpid, segmentationId, localPort, attachMac,
                                               Constants.CT_STATE_TRACKED_EXIST_PRIORITY, write );
            programConntrackInvDropRule(dpid, segmentationId, localPort, attachMac,
                                        Constants.CT_STATE_NEW_PRIORITY_DROP, write );
            programConntrackNewDropRule(dpid, segmentationId, localPort, attachMac,
                                             Constants.CT_STATE_NEW_PRIORITY_DROP, write );
            LOG.info("programIngressAclFixedConntrackRule :  default connection tracking rule are added.");
        } catch (Exception e) {
            LOG.error("Failed to add default conntrack rules : " , e);
        }
    }

    private void programConntrackUntrackRule(Long dpidLong, String segmentationId,
                                             long localPort, String attachMac, Integer priority, boolean write) {
        MatchBuilder matchBuilder = new MatchBuilder();
        String flowName = "Ingress_Fixed_Conntrk_Untrk_" + segmentationId + "_" + localPort + "_";
        matchBuilder = MatchUtils.createV4EtherMatchWithType(matchBuilder,null,attachMac,MatchUtils.ETHERTYPE_IPV4);
        matchBuilder = MatchUtils.addCtState(matchBuilder,MatchUtils.UNTRACKED_CT_STATE,
                                             MatchUtils.UNTRACKED_CT_STATE_MASK);
        FlowBuilder flowBuilder = FlowUtils.createFlowBuilder(flowName, priority, matchBuilder, getTable());
        addInstructionWithConntrackRecirc(flowBuilder);
        NodeBuilder nodeBuilder = FlowUtils.createNodeBuilder(dpidLong);
        syncFlow(flowBuilder ,nodeBuilder, write);
    }

    private void programConntrackTrackedPlusEstRule(Long dpidLong, String segmentationId,
                                                  long localPort, String attachMac,Integer priority, boolean write) {
        MatchBuilder matchBuilder = new MatchBuilder();
        String flowName = "Ingress_Fixed_Conntrk_TrkEst_" + segmentationId + "_" + localPort + "_";
        matchBuilder = MatchUtils.createV4EtherMatchWithType(matchBuilder,null,attachMac,MatchUtils.ETHERTYPE_IPV4);
        matchBuilder = MatchUtils.addCtState(matchBuilder,MatchUtils.TRACKED_EST_CT_STATE,
                                             MatchUtils.TRACKED_CT_STATE_MASK);
        FlowBuilder flowBuilder = FlowUtils.createFlowBuilder(flowName, priority, matchBuilder, getTable());
        addPipelineInstruction(flowBuilder, null, false);
        NodeBuilder nodeBuilder = FlowUtils.createNodeBuilder(dpidLong);
        syncFlow(flowBuilder ,nodeBuilder, write);
    }

    private void programConntrackTrackedPlusRelRule(Long dpidLong, String segmentationId,
                                                    long localPort, String attachMac,Integer priority, boolean write) {
        MatchBuilder matchBuilder = new MatchBuilder();
        String flowName = "Ingress_Fixed_Conntrk_TrkRel_" + segmentationId + "_" + localPort + "_";
        matchBuilder = MatchUtils.createV4EtherMatchWithType(matchBuilder,null,attachMac,MatchUtils.ETHERTYPE_IPV4);
        matchBuilder = MatchUtils.addCtState(matchBuilder,MatchUtils.TRACKED_REL_CT_STATE,
                                             MatchUtils.TRACKED_CT_STATE_MASK);
        FlowBuilder flowBuilder = FlowUtils.createFlowBuilder(flowName, priority, matchBuilder, getTable());
        addPipelineInstruction(flowBuilder, null, false);
        NodeBuilder nodeBuilder = FlowUtils.createNodeBuilder(dpidLong);
        syncFlow(flowBuilder ,nodeBuilder, write);
    }

    private void programConntrackNewDropRule(Long dpidLong, String segmentationId,
                                             long localPort, String attachMac, Integer priority, boolean write) {
        MatchBuilder matchBuilder = new MatchBuilder();

        String flowName = "Ingress_Fixed_Conntrk_NewDrop_" + segmentationId + "_" + localPort + "_";
        matchBuilder = MatchUtils.createV4EtherMatchWithType(matchBuilder,null,attachMac,0x0800L);
        matchBuilder = MatchUtils.addCtState(matchBuilder,MatchUtils.TRACKED_NEW_CT_STATE,
                                             MatchUtils.TRACKED_NEW_CT_STATE_MASK);
        FlowBuilder flowBuilder = FlowUtils.createFlowBuilder(flowName, priority, matchBuilder, getTable());
        addPipelineInstruction(flowBuilder, null, true);
        NodeBuilder nodeBuilder = FlowUtils.createNodeBuilder(dpidLong);
        syncFlow(flowBuilder ,nodeBuilder, write);
    }

    private void programConntrackInvDropRule(Long dpidLong, String segmentationId,
                                             long localPort, String attachMac, Integer priority, boolean write) {
        MatchBuilder matchBuilder = new MatchBuilder();
        String flowName = "Ingress_Fixed_Conntrk_InvDrop_" + segmentationId + "_" + localPort + "_";
        matchBuilder = MatchUtils.createV4EtherMatchWithType(matchBuilder,null,attachMac, MatchUtils.ETHERTYPE_IPV4);
        matchBuilder = MatchUtils.addCtState(matchBuilder,MatchUtils.TRACKED_INV_CT_STATE,
                                             MatchUtils.TRACKED_INV_CT_STATE_MASK);
        FlowBuilder flowBuilder = FlowUtils.createFlowBuilder(flowName, priority, matchBuilder, getTable());
        addPipelineInstruction(flowBuilder, null, true);
        NodeBuilder nodeBuilder = FlowUtils.createNodeBuilder(dpidLong);
        syncFlow(flowBuilder ,nodeBuilder, write);
    }

    /**
     * Allows an IPv4/v6 packet ingress to the destination mac address.
     * @param dpidLong the dpid
     * @param isIpv6 indicates whether this is an Ipv
     * @param segmentationId the segementation id
     * @param dstMac the destination mac address
     * @param write add or remove
     * @param protoPortMatchPriority the protocol match priority.
     */
    private void ingressAclIp(Long dpidLong, boolean isIpv6, String segmentationId, String dstMac,
                              boolean write, Integer protoPortMatchPriority ) {
        MatchBuilder matchBuilder = new MatchBuilder();
        String flowId = "Ingress_IP" + segmentationId + "_" + dstMac + "_Permit_";
        if (isIpv6) {
            matchBuilder = MatchUtils.createV6EtherMatchWithType(matchBuilder,null,dstMac);
        } else {
            matchBuilder = MatchUtils.createV4EtherMatchWithType(matchBuilder,null,dstMac,MatchUtils.ETHERTYPE_IPV4);
        }
        addConntrackMatch(matchBuilder, MatchUtils.TRACKED_NEW_CT_STATE,MatchUtils.TRACKED_NEW_CT_STATE_MASK);
        FlowBuilder flowBuilder = FlowUtils.createFlowBuilder(flowId, protoPortMatchPriority, matchBuilder, getTable());
        addInstructionWithConntrackCommit(flowBuilder, false);
        NodeBuilder nodeBuilder = FlowUtils.createNodeBuilder(dpidLong);
        syncFlow(flowBuilder ,nodeBuilder, write);
    }

    /**
     * Creates a ingress match to the dst macaddress. If src address is specified
     * source specific match will be created. Otherwise a match with a CIDR will
     * be created.
     * @param dpidLong the dpid
     * @param segmentationId the segmentation id
     * @param dstMac the destination mac address.
     * @param portSecurityRule the security rule in the SG
     * @param srcAddress the destination IP address
     * @param write add or delete
     * @param protoPortMatchPriority the protocol match priroty
     */
    private void ingressAclTcp(Long dpidLong, String segmentationId, String dstMac,
                               NeutronSecurityRule portSecurityRule, String srcAddress, boolean write,
                               Integer protoPortMatchPriority ) {
        boolean portRange = false;
        MatchBuilder matchBuilder = new MatchBuilder();
        String flowId = "Ingress_TCP_" + segmentationId + "_" + dstMac + "_";
        boolean isIpv6 = NeutronSecurityRule.ETHERTYPE_IPV6.equals(portSecurityRule.getSecurityRuleEthertype());
        if (isIpv6) {
            matchBuilder = MatchUtils.createV6EtherMatchWithType(matchBuilder,null,dstMac);
        } else {
            matchBuilder = MatchUtils.createV4EtherMatchWithType(matchBuilder,null,dstMac,MatchUtils.ETHERTYPE_IPV4);
        }

        /* Custom TCP Match*/
        if (portSecurityRule.getSecurityRulePortMin().equals(portSecurityRule.getSecurityRulePortMax())) {
            flowId = flowId + portSecurityRule.getSecurityRulePortMin() + "_";
            matchBuilder = MatchUtils.addLayer4Match(matchBuilder, MatchUtils.TCP_SHORT, 0,
                                                     portSecurityRule.getSecurityRulePortMin());
        } else {
            /* All TCP Match */
            if (portSecurityRule.getSecurityRulePortMin().equals(PORT_RANGE_MIN)
                    && portSecurityRule.getSecurityRulePortMax().equals(PORT_RANGE_MAX)) {
                flowId = flowId + portSecurityRule.getSecurityRulePortMin() + "_"
                    + portSecurityRule.getSecurityRulePortMax() + "_";
                matchBuilder = MatchUtils.addLayer4Match(matchBuilder, MatchUtils.TCP_SHORT, 0, 0);
            } else {
                portRange = true;
            }
        }
        if (null != srcAddress) {
            flowId = flowId + srcAddress;
            if (isIpv6) {
                matchBuilder = MatchUtils.addRemoteIpv6Prefix(matchBuilder,
                        MatchUtils.iPv6PrefixFromIPv6Address(srcAddress),null);
            } else {
                matchBuilder = MatchUtils.addRemoteIpPrefix(matchBuilder,
                        MatchUtils.iPv4PrefixFromIPv4Address(srcAddress),null);
            }
        } else if (null != portSecurityRule.getSecurityRuleRemoteIpPrefix()) {
            flowId = flowId + portSecurityRule.getSecurityRuleRemoteIpPrefix();
            if (isIpv6) {
                matchBuilder = MatchUtils.addRemoteIpv6Prefix(matchBuilder,
                        new Ipv6Prefix(portSecurityRule
                                       .getSecurityRuleRemoteIpPrefix()),null);
            } else {
                matchBuilder = MatchUtils.addRemoteIpPrefix(matchBuilder,
                        new Ipv4Prefix(portSecurityRule
                                       .getSecurityRuleRemoteIpPrefix()),null);
            }
        }
        NodeBuilder nodeBuilder = FlowUtils.createNodeBuilder(dpidLong);
        if (portRange) {
            Map<Integer, Integer> portMaskMap = MatchUtils
                    .getLayer4MaskForRange(portSecurityRule.getSecurityRulePortMin(),
                                           portSecurityRule.getSecurityRulePortMax());
            for (Integer port: portMaskMap.keySet()) {
                String rangeflowId = flowId + port + "_" + portMaskMap.get(port) + "_";
                rangeflowId = rangeflowId + "_Permit";
                MatchUtils.addLayer4MatchWithMask(matchBuilder, MatchUtils.TCP_SHORT,
                                                  0, port, portMaskMap.get(port));
                addConntrackMatch(matchBuilder, MatchUtils.TRACKED_NEW_CT_STATE,MatchUtils.TRACKED_NEW_CT_STATE_MASK);
                FlowBuilder flowBuilder = FlowUtils.createFlowBuilder(rangeflowId, protoPortMatchPriority,
                                                                      matchBuilder, getTable());
                addInstructionWithConntrackCommit(flowBuilder, false);
                syncFlow(flowBuilder ,nodeBuilder, write);
            }
        } else {
            flowId = flowId + "_Permit";
            addConntrackMatch(matchBuilder, MatchUtils.TRACKED_NEW_CT_STATE,MatchUtils.TRACKED_NEW_CT_STATE_MASK);
            FlowBuilder flowBuilder = FlowUtils.createFlowBuilder(flowId, protoPortMatchPriority,
                                                                  matchBuilder, getTable());
            addInstructionWithConntrackCommit(flowBuilder, false);
            syncFlow(flowBuilder ,nodeBuilder, write);
        }
    }

    /**
     * Creates a ingress match to the dst macaddress. If src address is specified
     * source specific match will be created. Otherwise a match with a CIDR will
     * be created.
     * @param dpidLong the dpid
     * @param segmentationId the segmentation id
     * @param dstMac the destination mac address.
     * @param portSecurityRule the security rule in the SG
     * @param srcAddress the destination IP address
     * @param write add or delete
     * @param protoPortMatchPriority the protocol match priroty
     */
    private void ingressAclUdp(Long dpidLong, String segmentationId, String dstMac,
                               NeutronSecurityRule portSecurityRule, String srcAddress,
                               boolean write, Integer protoPortMatchPriority ) {
        boolean portRange = false;
        boolean isIpv6 = NeutronSecurityRule.ETHERTYPE_IPV6.equals(portSecurityRule.getSecurityRuleEthertype());
        MatchBuilder matchBuilder = new MatchBuilder();
        String flowId = "Ingress_UDP_" + segmentationId + "_" + dstMac + "_";
        if (isIpv6)  {
            matchBuilder = MatchUtils.createV6EtherMatchWithType(matchBuilder,null,dstMac);
        } else {
            matchBuilder = MatchUtils.createV4EtherMatchWithType(matchBuilder,null,dstMac,MatchUtils.ETHERTYPE_IPV4);
        }

        /* Custom UDP Match */
        if (portSecurityRule.getSecurityRulePortMin().equals(portSecurityRule.getSecurityRulePortMax())) {
            flowId = flowId + portSecurityRule.getSecurityRulePortMin() + "_";
            matchBuilder = MatchUtils.addLayer4Match(matchBuilder, MatchUtils.UDP_SHORT, 0,
                                                     portSecurityRule.getSecurityRulePortMin());
        } else {
            /* All UDP Match */
            if (portSecurityRule.getSecurityRulePortMin().equals(PORT_RANGE_MIN)
                    && portSecurityRule.getSecurityRulePortMax().equals(PORT_RANGE_MAX)) {
                flowId = flowId + portSecurityRule.getSecurityRulePortMin() + "_"
                    + portSecurityRule.getSecurityRulePortMax() + "_";
                matchBuilder = MatchUtils.addLayer4Match(matchBuilder, MatchUtils.UDP_SHORT, 0, 0);
            } else {
                portRange = true;
            }
        }
        if (null != srcAddress) {
            flowId = flowId + srcAddress;
            if (isIpv6) {
                matchBuilder = MatchUtils.addRemoteIpv6Prefix(matchBuilder,
                        MatchUtils.iPv6PrefixFromIPv6Address(srcAddress), null);
            } else {
                matchBuilder = MatchUtils.addRemoteIpPrefix(matchBuilder,
                        MatchUtils.iPv4PrefixFromIPv4Address(srcAddress), null);
            }
        } else if (null != portSecurityRule.getSecurityRuleRemoteIpPrefix()) {
            flowId = flowId + portSecurityRule.getSecurityRuleRemoteIpPrefix();
            if (isIpv6) {
                matchBuilder = MatchUtils.addRemoteIpv6Prefix(matchBuilder,
                        new Ipv6Prefix(portSecurityRule
                                       .getSecurityRuleRemoteIpPrefix()),null);
            } else {
                matchBuilder = MatchUtils.addRemoteIpPrefix(matchBuilder,
                        new Ipv4Prefix(portSecurityRule
                                       .getSecurityRuleRemoteIpPrefix()),null);
            }
        }
        NodeBuilder nodeBuilder = FlowUtils.createNodeBuilder(dpidLong);
        if (portRange) {
            Map<Integer, Integer> portMaskMap = MatchUtils
                    .getLayer4MaskForRange(portSecurityRule.getSecurityRulePortMin(),
                                           portSecurityRule.getSecurityRulePortMax());
            for (Integer port: portMaskMap.keySet()) {
                String rangeflowId = flowId + port + "_" + portMaskMap.get(port) + "_";
                rangeflowId = rangeflowId + "_Permit";
                MatchUtils.addLayer4MatchWithMask(matchBuilder, MatchUtils.UDP_SHORT,
                                                   0, port, portMaskMap.get(port));
                addConntrackMatch(matchBuilder, MatchUtils.TRACKED_NEW_CT_STATE,MatchUtils.TRACKED_NEW_CT_STATE_MASK);
                FlowBuilder flowBuilder = FlowUtils.createFlowBuilder(rangeflowId, protoPortMatchPriority,
                                                                      matchBuilder, getTable());
                addInstructionWithConntrackCommit(flowBuilder, false);
                syncFlow(flowBuilder ,nodeBuilder, write);
            }
        } else {
            flowId = flowId + "_Permit";
            addConntrackMatch(matchBuilder, MatchUtils.TRACKED_NEW_CT_STATE,MatchUtils.TRACKED_NEW_CT_STATE_MASK);
            FlowBuilder flowBuilder = FlowUtils.createFlowBuilder(flowId, protoPortMatchPriority,
                                                                  matchBuilder, getTable());
            addInstructionWithConntrackCommit(flowBuilder, false);
            syncFlow(flowBuilder ,nodeBuilder, write);
        }
    }

    private void ingressAclIcmp(Long dpidLong, String segmentationId, String dstMac,
            NeutronSecurityRule portSecurityRule, String srcAddress,
            boolean write, Integer protoPortMatchPriority) {

        boolean isIpv6 = NeutronSecurityRule.ETHERTYPE_IPV6.equals(portSecurityRule.getSecurityRuleEthertype());
        if (isIpv6) {
            ingressAclIcmpV6(dpidLong, segmentationId, dstMac, portSecurityRule, srcAddress,
                             write, protoPortMatchPriority);
        } else {
            ingressAclIcmpV4(dpidLong, segmentationId, dstMac, portSecurityRule, srcAddress,
                             write, protoPortMatchPriority);
        }
    }

    /**
     * Creates a ingress icmp match to the dst macaddress. If src address is specified
     * source specific match will be created. Otherwise a match with a CIDR will
     * be created.
     * @param dpidLong the dpid
     * @param segmentationId the segmentation id
     * @param dstMac the destination mac address.
     * @param portSecurityRule the security rule in the SG
     * @param srcAddress the destination IP address
     * @param write add or delete
     * @param protoPortMatchPriority the protocol match priority
     */
    private void ingressAclIcmpV4(Long dpidLong, String segmentationId, String dstMac,
                                  NeutronSecurityRule portSecurityRule, String srcAddress,
                                  boolean write, Integer protoPortMatchPriority) {

        MatchBuilder matchBuilder = new MatchBuilder();
        String flowId = "Ingress_ICMP_" + segmentationId + "_" + dstMac + "_";
        matchBuilder = MatchUtils.createV4EtherMatchWithType(matchBuilder,null,dstMac,MatchUtils.ETHERTYPE_IPV4);

        /* Custom ICMP Match */
        if (portSecurityRule.getSecurityRulePortMin() != null
                && portSecurityRule.getSecurityRulePortMax() != null) {
            flowId = flowId + portSecurityRule.getSecurityRulePortMin().shortValue() + "_"
                    + portSecurityRule.getSecurityRulePortMax().shortValue() + "_";
            matchBuilder = MatchUtils.createICMPv4Match(matchBuilder,
                    portSecurityRule.getSecurityRulePortMin().shortValue(),
                    portSecurityRule.getSecurityRulePortMax().shortValue());
        } else {
            /* All ICMP Match */
            flowId = flowId + "all" + "_";
            matchBuilder = MatchUtils.createICMPv4Match(matchBuilder,MatchUtils.ALL_ICMP, MatchUtils.ALL_ICMP);
        }
        if (null != srcAddress) {
            flowId = flowId + srcAddress;
            matchBuilder = MatchUtils.addRemoteIpPrefix(matchBuilder,
                   MatchUtils.iPv4PrefixFromIPv4Address(srcAddress), null);
        } else if (null != portSecurityRule.getSecurityRuleRemoteIpPrefix()) {
            flowId = flowId + portSecurityRule.getSecurityRuleRemoteIpPrefix();
            if (!portSecurityRule.getSecurityRuleRemoteIpPrefix().contains("/0")) {
                matchBuilder = MatchUtils.addRemoteIpPrefix(matchBuilder,
                                         new Ipv4Prefix(portSecurityRule.getSecurityRuleRemoteIpPrefix()),null);
            }
        }
        flowId = flowId + "_Permit";
        addConntrackMatch(matchBuilder, MatchUtils.TRACKED_NEW_CT_STATE,MatchUtils.TRACKED_NEW_CT_STATE_MASK);
        FlowBuilder flowBuilder = FlowUtils.createFlowBuilder(flowId, protoPortMatchPriority, matchBuilder, getTable());
        addInstructionWithConntrackCommit(flowBuilder, false);
        NodeBuilder nodeBuilder = FlowUtils.createNodeBuilder(dpidLong);
        syncFlow(flowBuilder ,nodeBuilder, write);
    }

    /**
     * Creates a ingress icmpv6 match to the dst macaddress. If src address is specified
     * source specific match will be created. Otherwise a match with a CIDR will
     * be created.
     * @param dpidLong the dpid
     * @param segmentationId the segmentation id
     * @param dstMac the destination mac address.
     * @param portSecurityRule the security rule in the SG
     * @param srcAddress the destination IP address
     * @param write add or delete
     * @param protoPortMatchPriority the protocol match priority
     */
    private void ingressAclIcmpV6(Long dpidLong, String segmentationId, String dstMac,
                                  NeutronSecurityRule portSecurityRule, String srcAddress,
                                  boolean write, Integer protoPortMatchPriority) {

        MatchBuilder matchBuilder = new MatchBuilder();
        String flowId = "Ingress_ICMP_" + segmentationId + "_" + dstMac + "_";
        matchBuilder = MatchUtils.createV6EtherMatchWithType(matchBuilder,null,dstMac);

        /* Custom ICMP Match */
        if (portSecurityRule.getSecurityRulePortMin() != null
                && portSecurityRule.getSecurityRulePortMax() != null) {
            flowId = flowId + portSecurityRule.getSecurityRulePortMin().shortValue() + "_"
                    + portSecurityRule.getSecurityRulePortMax().shortValue() + "_";
            matchBuilder = MatchUtils.createICMPv6Match(matchBuilder,
                    portSecurityRule.getSecurityRulePortMin().shortValue(),
                    portSecurityRule.getSecurityRulePortMax().shortValue());
        } else {
            /* All ICMP Match */
            flowId = flowId + "all" + "_";
            matchBuilder = MatchUtils.createICMPv6Match(matchBuilder,MatchUtils.ALL_ICMP, MatchUtils.ALL_ICMP);
        }
        if (null != srcAddress) {
            flowId = flowId + srcAddress;
            matchBuilder = MatchUtils.addRemoteIpv6Prefix(matchBuilder,
                    MatchUtils.iPv6PrefixFromIPv6Address(srcAddress), null);
        } else if (null != portSecurityRule.getSecurityRuleRemoteIpPrefix()) {
            flowId = flowId + portSecurityRule.getSecurityRuleRemoteIpPrefix();
            matchBuilder = MatchUtils.addRemoteIpv6Prefix(matchBuilder,
                    new Ipv6Prefix(portSecurityRule
                                   .getSecurityRuleRemoteIpPrefix()),null);
        }
        addConntrackMatch(matchBuilder, MatchUtils.TRACKED_NEW_CT_STATE,MatchUtils.TRACKED_NEW_CT_STATE_MASK);
        NodeBuilder nodeBuilder = FlowUtils.createNodeBuilder(dpidLong);
        flowId = flowId + "_Permit";
        FlowBuilder flowBuilder = FlowUtils.createFlowBuilder(flowId, protoPortMatchPriority, matchBuilder, getTable());
        addInstructionWithConntrackCommit(flowBuilder, false);
        syncFlow(flowBuilder ,nodeBuilder, write);
    }

    /**
     * Add rule to ensure only DHCP server traffic from the specified mac is allowed.
     *
     * @param dpidLong the dpid
     * @param segmentationId the segmentation id
     * @param dhcpMacAddress the DHCP server mac address
     * @param attachMac the mac address of  the port
     * @param write is write or delete
     * @param protoPortMatchPriority the priority
     */
    private void ingressAclDhcpAllowServerTraffic(Long dpidLong, String segmentationId, String dhcpMacAddress,
                                                  String attachMac, boolean write, Integer protoPortMatchPriority) {

        MatchBuilder matchBuilder = new MatchBuilder();
        matchBuilder = MatchUtils.createV4EtherMatchWithType(matchBuilder,dhcpMacAddress,attachMac,
                                                             MatchUtils.ETHERTYPE_IPV4);
        MatchUtils.addLayer4Match(matchBuilder, MatchUtils.UDP_SHORT, 67, 68);
        String flowId = "Ingress_DHCP_Server" + segmentationId + "_" + dhcpMacAddress + "_Permit_";
        FlowBuilder flowBuilder = FlowUtils.createFlowBuilder(flowId, protoPortMatchPriority, matchBuilder, getTable());
        addPipelineInstruction(flowBuilder, null, false);
        NodeBuilder nodeBuilder = FlowUtils.createNodeBuilder(dpidLong);
        syncFlow(flowBuilder ,nodeBuilder, write);
    }

    /**
     * Add rule to ensure only DHCPv6 server traffic from the specified mac is allowed.
     *
     * @param dpidLong the dpid
     * @param segmentationId the segmentation id
     * @param dhcpMacAddress the DHCP server mac address
     * @param attachMac the mac address of  the port
     * @param write is write or delete
     * @param protoPortMatchPriority the priority
     */
    private void ingressAclDhcpv6AllowServerTraffic(Long dpidLong, String segmentationId, String dhcpMacAddress,
                                                    String attachMac, boolean write, Integer protoPortMatchPriority) {

        MatchBuilder matchBuilder = new MatchBuilder();
        matchBuilder = MatchUtils.createV4EtherMatchWithType(matchBuilder,dhcpMacAddress,attachMac,
                                                             MatchUtils.ETHERTYPE_IPV6);
        MatchUtils.addLayer4Match(matchBuilder, MatchUtils.UDP_SHORT, 547, 546);
        String flowId = "Ingress_DHCPv6_Server" + segmentationId + "_" + dhcpMacAddress + "_Permit_";
        FlowBuilder flowBuilder = FlowUtils.createFlowBuilder(flowId, protoPortMatchPriority, matchBuilder, getTable());
        addPipelineInstruction(flowBuilder, null, false);
        NodeBuilder nodeBuilder = FlowUtils.createNodeBuilder(dpidLong);
        syncFlow(flowBuilder ,nodeBuilder, write);
    }

    private void addConntrackMatch(MatchBuilder matchBuilder, int state, int mask) {
        if (securityServicesManager.isConntrackEnabled()) {
            MatchUtils.addCtState(matchBuilder, state, mask );
        }

    }

    private FlowBuilder addInstructionWithConntrackCommit( FlowBuilder flowBuilder , boolean isDrop) {
        InstructionBuilder instructionBuilder = null;
        if (securityServicesManager.isConntrackEnabled()) {
            Action conntrackAction = ActionUtils.nxConntrackAction(1, 0L, 0, (short)0xff);
            instructionBuilder = InstructionUtils
                    .createInstructionBuilder(ActionUtils.conntrackActionBuilder(conntrackAction), 1, false);
        }
        return addPipelineInstruction(flowBuilder,instructionBuilder, isDrop);
    }

    private FlowBuilder addInstructionWithConntrackRecirc( FlowBuilder flowBuilder) {
        InstructionBuilder instructionBuilder = null;
        if (securityServicesManager.isConntrackEnabled()) {
            Action conntrackAction = ActionUtils.nxConntrackAction(0, 0L, 0, (short)0x0);
            instructionBuilder = InstructionUtils
                    .createInstructionBuilder(ActionUtils.conntrackActionBuilder(conntrackAction), 1, false);
            List<Instruction> instructionsList = Lists.newArrayList();
            instructionsList.add(instructionBuilder.build());
            InstructionsBuilder isb = new InstructionsBuilder();
            isb.setInstruction(instructionsList);
            flowBuilder.setInstructions(isb.build());
        }
        return flowBuilder;
    }

    private FlowBuilder addPipelineInstruction( FlowBuilder flowBuilder , InstructionBuilder instructionBuilder,
                                                boolean isDrop) {
        InstructionBuilder pipeLineIndstructionBuilder = createPipleLineInstructionBuilder(isDrop);
        List<Instruction> instructionsList = Lists.newArrayList();
        instructionsList.add(pipeLineIndstructionBuilder.build());
        if (null != instructionBuilder) {
            instructionsList.add(instructionBuilder.build());
        }
        InstructionsBuilder isb = new InstructionsBuilder();
        isb.setInstruction(instructionsList);
        flowBuilder.setInstructions(isb.build());
        return flowBuilder;
    }

    private InstructionBuilder createPipleLineInstructionBuilder(boolean drop) {
        InstructionBuilder ib = this.getMutablePipelineInstructionBuilder();
        if (drop) {
            InstructionUtils.createDropInstructions(ib);
        }
        ib.setOrder(0);
        List<Instruction> instructionsList = Lists.newArrayList();
        ib.setKey(new InstructionKey(0));
        instructionsList.add(ib.build());
        return ib;
    }
    /**
     * Add or remove flow to the node.
     * @param flowBuilder the flow builder
     * @param nodeBuilder the node builder
     * @param write whether it is a write
     */
    private void syncFlow(FlowBuilder flowBuilder, NodeBuilder nodeBuilder,
                          boolean write) {
        if (write) {
            writeFlow(flowBuilder, nodeBuilder);
        } else {
            removeFlow(flowBuilder, nodeBuilder);
        }
    }

    private List<NeutronSecurityRule> getSecurityRulesforGroup(NeutronSecurityGroup securityGroup) {
        List<NeutronSecurityRule> securityRules = new ArrayList<>();
        List<NeutronSecurityRule> rules = neutronSecurityRule.getAllNeutronSecurityRules();
        for (NeutronSecurityRule securityRule : rules) {
            if (securityGroup.getID().equals(securityRule.getSecurityRuleGroupID())) {
                securityRules.add(securityRule);
            }
        }
        return securityRules;
    }

    @Override
    public void setDependencies(BundleContext bundleContext, ServiceReference serviceReference) {
        super.setDependencies(bundleContext.getServiceReference(IngressAclProvider.class.getName()), this);
        securityServicesManager =
                (SecurityServicesManager) ServiceHelper.getGlobalInstance(SecurityServicesManager.class, this);
        securityGroupCacheManger =
                (SecurityGroupCacheManger) ServiceHelper.getGlobalInstance(SecurityGroupCacheManger.class, this);
        neutronSecurityRule = (INeutronSecurityRuleCRUD) ServiceHelper.getGlobalInstance(INeutronSecurityRuleCRUD.class, this);
    }

    @Override
    public void setDependencies(Object impl) {

    }
}
