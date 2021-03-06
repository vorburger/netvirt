/*
 * Copyright (c) 2015, 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.openstack.netvirt;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.netvirt.openstack.netvirt.api.ArpProvider;
import org.opendaylight.netvirt.openstack.netvirt.api.BridgeConfigurationManager;
import org.opendaylight.netvirt.openstack.netvirt.api.ConfigurationService;
import org.opendaylight.netvirt.openstack.netvirt.api.Constants;
import org.opendaylight.netvirt.openstack.netvirt.api.EgressAclProvider;
import org.opendaylight.netvirt.openstack.netvirt.api.EventDispatcher;
import org.opendaylight.netvirt.openstack.netvirt.api.GatewayMacResolver;
import org.opendaylight.netvirt.openstack.netvirt.api.GatewayMacResolverListener;
import org.opendaylight.netvirt.openstack.netvirt.api.IcmpEchoProvider;
import org.opendaylight.netvirt.openstack.netvirt.api.InboundNatProvider;
import org.opendaylight.netvirt.openstack.netvirt.api.IngressAclProvider;
import org.opendaylight.netvirt.openstack.netvirt.api.L3ForwardingProvider;
import org.opendaylight.netvirt.openstack.netvirt.api.LoadBalancerProvider;
import org.opendaylight.netvirt.openstack.netvirt.api.MultiTenantAwareRouter;
import org.opendaylight.netvirt.openstack.netvirt.api.NetworkingProviderManager;
import org.opendaylight.netvirt.openstack.netvirt.api.NodeCacheListener;
import org.opendaylight.netvirt.openstack.netvirt.api.NodeCacheManager;
import org.opendaylight.netvirt.openstack.netvirt.api.OutboundNatProvider;
import org.opendaylight.netvirt.openstack.netvirt.api.OvsdbInventoryListener;
import org.opendaylight.netvirt.openstack.netvirt.api.OvsdbInventoryService;
import org.opendaylight.netvirt.openstack.netvirt.api.RoutingProvider;
import org.opendaylight.netvirt.openstack.netvirt.api.SecurityGroupCacheManger;
import org.opendaylight.netvirt.openstack.netvirt.api.SecurityServicesManager;
import org.opendaylight.netvirt.openstack.netvirt.api.Southbound;
import org.opendaylight.netvirt.openstack.netvirt.api.TenantNetworkManager;
import org.opendaylight.netvirt.openstack.netvirt.api.VlanConfigurationCache;
import org.opendaylight.netvirt.openstack.netvirt.impl.BridgeConfigurationManagerImpl;
import org.opendaylight.netvirt.openstack.netvirt.impl.ConfigurationServiceImpl;
import org.opendaylight.netvirt.openstack.netvirt.impl.DistributedArpService;
import org.opendaylight.netvirt.openstack.netvirt.impl.EventDispatcherImpl;
import org.opendaylight.netvirt.openstack.netvirt.impl.HostConfigService;
import org.opendaylight.netvirt.openstack.netvirt.impl.NeutronL3Adapter;
import org.opendaylight.netvirt.openstack.netvirt.impl.NodeCacheManagerImpl;
import org.opendaylight.netvirt.openstack.netvirt.impl.OpenstackRouter;
import org.opendaylight.netvirt.openstack.netvirt.impl.OvsdbInventoryServiceImpl;
import org.opendaylight.netvirt.openstack.netvirt.impl.ProviderNetworkManagerImpl;
import org.opendaylight.netvirt.openstack.netvirt.impl.SecurityGroupCacheManagerImpl;
import org.opendaylight.netvirt.openstack.netvirt.impl.SecurityServicesImpl;
import org.opendaylight.netvirt.openstack.netvirt.impl.SouthboundImpl;
import org.opendaylight.netvirt.openstack.netvirt.impl.TenantNetworkManagerImpl;
import org.opendaylight.netvirt.openstack.netvirt.impl.VlanConfigurationCacheImpl;
import org.opendaylight.netvirt.openstack.netvirt.translator.crud.INeutronFloatingIPCRUD;
import org.opendaylight.netvirt.openstack.netvirt.translator.crud.INeutronLoadBalancerCRUD;
import org.opendaylight.netvirt.openstack.netvirt.translator.crud.INeutronLoadBalancerPoolCRUD;
import org.opendaylight.netvirt.openstack.netvirt.translator.crud.INeutronNetworkCRUD;
import org.opendaylight.netvirt.openstack.netvirt.translator.crud.INeutronPortCRUD;
import org.opendaylight.netvirt.openstack.netvirt.translator.crud.INeutronSubnetCRUD;
import org.opendaylight.netvirt.openstack.netvirt.translator.crud.impl.NeutronFirewallInterface;
import org.opendaylight.netvirt.openstack.netvirt.translator.crud.impl.NeutronFirewallPolicyInterface;
import org.opendaylight.netvirt.openstack.netvirt.translator.crud.impl.NeutronFirewallRuleInterface;
import org.opendaylight.netvirt.openstack.netvirt.translator.crud.impl.NeutronFloatingIPInterface;
import org.opendaylight.netvirt.openstack.netvirt.translator.crud.impl.NeutronLoadBalancerHealthMonitorInterface;
import org.opendaylight.netvirt.openstack.netvirt.translator.crud.impl.NeutronLoadBalancerInterface;
import org.opendaylight.netvirt.openstack.netvirt.translator.crud.impl.NeutronLoadBalancerListenerInterface;
import org.opendaylight.netvirt.openstack.netvirt.translator.crud.impl.NeutronLoadBalancerPoolInterface;
import org.opendaylight.netvirt.openstack.netvirt.translator.crud.impl.NeutronLoadBalancerPoolMemberInterface;
import org.opendaylight.netvirt.openstack.netvirt.translator.crud.impl.NeutronNetworkInterface;
import org.opendaylight.netvirt.openstack.netvirt.translator.crud.impl.NeutronPortInterface;
import org.opendaylight.netvirt.openstack.netvirt.translator.crud.impl.NeutronRouterInterface;
import org.opendaylight.netvirt.openstack.netvirt.translator.crud.impl.NeutronSecurityGroupInterface;
import org.opendaylight.netvirt.openstack.netvirt.translator.crud.impl.NeutronSecurityRuleInterface;
import org.opendaylight.netvirt.openstack.netvirt.translator.crud.impl.NeutronSubnetInterface;
import org.opendaylight.netvirt.openstack.netvirt.translator.iaware.INeutronFirewallAware;
import org.opendaylight.netvirt.openstack.netvirt.translator.iaware.INeutronFirewallRuleAware;
import org.opendaylight.netvirt.openstack.netvirt.translator.iaware.INeutronFirewallPolicyAware;
import org.opendaylight.netvirt.openstack.netvirt.translator.iaware.INeutronFloatingIPAware;
import org.opendaylight.netvirt.openstack.netvirt.translator.iaware.INeutronLoadBalancerAware;
import org.opendaylight.netvirt.openstack.netvirt.translator.iaware.INeutronLoadBalancerPoolAware;
import org.opendaylight.netvirt.openstack.netvirt.translator.iaware.INeutronLoadBalancerPoolMemberAware;
import org.opendaylight.netvirt.openstack.netvirt.translator.iaware.INeutronNetworkAware;
import org.opendaylight.netvirt.openstack.netvirt.translator.iaware.INeutronPortAware;
import org.opendaylight.netvirt.openstack.netvirt.translator.iaware.INeutronRouterAware;
import org.opendaylight.netvirt.openstack.netvirt.translator.iaware.INeutronSecurityGroupAware;
import org.opendaylight.netvirt.openstack.netvirt.translator.iaware.INeutronSecurityRuleAware;
import org.opendaylight.netvirt.openstack.netvirt.translator.iaware.INeutronSubnetAware;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigActivator implements BundleActivator {
    private static final Logger LOG = LoggerFactory.getLogger(ConfigActivator.class);
    private List<ServiceRegistration<?>> translatorCRUDRegistrations = new ArrayList<>();
    private List<Pair<Object, ServiceRegistration>> servicesAndRegistrations = new ArrayList<>();
    private final DataBroker dataBroker;
    private boolean conntrackEnabled = false;
    private boolean intBridgeGenMac = true;

    public ConfigActivator(final DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    @Override
    public void start(BundleContext context) throws Exception {
        LOG.info("ConfigActivator start:");
        registerCRUDServiceProviders(context);

        ConfigurationServiceImpl configurationService = new ConfigurationServiceImpl();
        registerService(context, new String[] {ConfigurationService.class.getName()},
                null, configurationService);

        BridgeConfigurationManagerImpl bridgeConfigurationManager = new BridgeConfigurationManagerImpl(intBridgeGenMac);
        registerService(context, new String[] {BridgeConfigurationManager.class.getName()},
                null, bridgeConfigurationManager);

        final TenantNetworkManagerImpl tenantNetworkManager = new TenantNetworkManagerImpl();
        registerService(context, new String[] {TenantNetworkManager.class.getName()},
                null, tenantNetworkManager);

        VlanConfigurationCacheImpl vlanConfigurationCache = new VlanConfigurationCacheImpl();
        registerService(context, new String[] {VlanConfigurationCache.class.getName()},
                null, vlanConfigurationCache);

        FloatingIPHandler floatingIPHandler = new FloatingIPHandler();
        registerAbstractHandlerService(context, new Class[] {INeutronFloatingIPAware.class},
                AbstractEvent.HandlerType.NEUTRON_FLOATING_IP, floatingIPHandler);

        final NetworkHandler networkHandler = new NetworkHandler();
        registerAbstractHandlerService(context, new Class[] {INeutronNetworkAware.class},
                AbstractEvent.HandlerType.NEUTRON_NETWORK, networkHandler);

        SubnetHandler subnetHandler = new SubnetHandler();
        registerAbstractHandlerService(context, new Class[] {INeutronSubnetAware.class},
                AbstractEvent.HandlerType.NEUTRON_SUBNET, subnetHandler);

        PortHandler portHandler = new PortHandler();
        registerAbstractHandlerService(context, new Class[] {INeutronPortAware.class},
                AbstractEvent.HandlerType.NEUTRON_PORT, portHandler);

        RouterHandler routerHandler = new RouterHandler();
        registerAbstractHandlerService(context, new Class[] {INeutronRouterAware.class},
                AbstractEvent.HandlerType.NEUTRON_ROUTER, routerHandler);

        SouthboundHandler southboundHandler = new SouthboundHandler();
        registerAbstractHandlerService(context, new Class[] {OvsdbInventoryListener.class, NodeCacheListener.class},
                AbstractEvent.HandlerType.SOUTHBOUND, southboundHandler);

        final LBaaSHandler lBaaSHandler = new LBaaSHandler();
        registerAbstractHandlerService(context, new Class[] {INeutronLoadBalancerAware.class, NodeCacheListener.class},
                AbstractEvent.HandlerType.NEUTRON_LOAD_BALANCER, lBaaSHandler);

        final LBaaSPoolHandler lBaaSPoolHandler = new LBaaSPoolHandler();
        registerAbstractHandlerService(context, new Class[] {INeutronLoadBalancerPoolAware.class},
                AbstractEvent.HandlerType.NEUTRON_LOAD_BALANCER_POOL, lBaaSPoolHandler);

        final LBaaSPoolMemberHandler lBaaSPoolMemberHandler = new LBaaSPoolMemberHandler();
        registerAbstractHandlerService(context, new Class[] {INeutronLoadBalancerPoolMemberAware.class},
                AbstractEvent.HandlerType.NEUTRON_LOAD_BALANCER_POOL_MEMBER, lBaaSPoolMemberHandler);

        PortSecurityHandler portSecurityHandler = new PortSecurityHandler();
        registerAbstractHandlerService(context,
                new Class[] {INeutronSecurityRuleAware.class, INeutronSecurityGroupAware.class},
                AbstractEvent.HandlerType.NEUTRON_PORT_SECURITY, portSecurityHandler);

        final SecurityServicesImpl securityServices = new SecurityServicesImpl(conntrackEnabled);
        registerService(context,
                new String[]{SecurityServicesManager.class.getName()}, null, securityServices);

        final SecurityGroupCacheManger securityGroupCacheManger = new SecurityGroupCacheManagerImpl();
        registerService(context,
                        new String[]{SecurityGroupCacheManger.class.getName()}, null, securityGroupCacheManger);

        registerService(context,
                new String[]{SecurityServicesManager.class.getName()}, null, securityServices);

        FWaasHandler fWaasHandler = new FWaasHandler();
        registerAbstractHandlerService(context,
                new Class[] {INeutronFirewallAware.class, INeutronFirewallRuleAware.class, INeutronFirewallPolicyAware.class},
                AbstractEvent.HandlerType.NEUTRON_FWAAS, fWaasHandler);

        ProviderNetworkManagerImpl providerNetworkManager = new ProviderNetworkManagerImpl();
        registerService(context,
                new String[]{NetworkingProviderManager.class.getName()}, null, providerNetworkManager);

        EventDispatcherImpl eventDispatcher = new EventDispatcherImpl();
        registerService(context,
                new String[]{EventDispatcher.class.getName()}, null, eventDispatcher);

        final NeutronL3Adapter neutronL3Adapter = new NeutronL3Adapter(
                new NeutronModelsDataStoreHelper(dataBroker));
        registerAbstractHandlerService(context, new Class[] {NeutronL3Adapter.class, GatewayMacResolverListener.class},
                AbstractEvent.HandlerType.NEUTRON_L3_ADAPTER, neutronL3Adapter);

        // TODO Why is DistributedArpService registered as an event handler without being an AbstractHandlerService?
        Dictionary<String, Object> distributedArpServiceProperties = new Hashtable<>();
        distributedArpServiceProperties.put(Constants.EVENT_HANDLER_TYPE_PROPERTY,
                AbstractEvent.HandlerType.DISTRIBUTED_ARP_SERVICE);
        final DistributedArpService distributedArpService = new DistributedArpService();
        registerService(context,
                new String[] {DistributedArpService.class.getName()},
                distributedArpServiceProperties, distributedArpService);

        OpenstackRouter openstackRouter = new OpenstackRouter();
        registerService(context,
                new String[]{MultiTenantAwareRouter.class.getName()}, null, openstackRouter);

        Southbound southbound = new SouthboundImpl(dataBroker);
        registerService(context,
                new String[]{Southbound.class.getName()}, null, southbound);

        HostConfigService hostConfigService = new HostConfigService(dataBroker);
        registerService(context,
                new String[]{HostConfigService.class.getName()}, null, hostConfigService);

        NodeCacheManagerImpl nodeCacheManager = new NodeCacheManagerImpl();
        registerAbstractHandlerService(context, new Class[] {NodeCacheManager.class},
                AbstractEvent.HandlerType.NODE, nodeCacheManager);

        OvsdbInventoryServiceImpl ovsdbInventoryService = new OvsdbInventoryServiceImpl(dataBroker);
        registerService(context,
                new String[] {OvsdbInventoryService.class.getName()}, null, ovsdbInventoryService);

        // Call .setDependencies() starting with the last service registered
        for (int i = servicesAndRegistrations.size() - 1; i >= 0; i--) {
            Pair<Object, ServiceRegistration> serviceAndRegistration = servicesAndRegistrations.get(i);
            Object service = serviceAndRegistration.getLeft();
            ServiceRegistration<?> serviceRegistration = serviceAndRegistration.getRight();
            LOG.info("Setting dependencies on service {}/{}, {}", i, servicesAndRegistrations.size(),
                    service.getClass());
            if (service instanceof ConfigInterface) {
                ((ConfigInterface) service).setDependencies(
                        serviceRegistration != null ? serviceRegistration.getReference() : null);
                LOG.info("Dependencies set");
            } else {
                LOG.warn("Service isn't a ConfigInterface");
            }
        }

        // TODO check if services are already available and setDependencies
        // addingService may not be called if the service is already available when the ServiceTracker
        // is started
        trackService(context, INeutronNetworkCRUD.class, tenantNetworkManager, networkHandler, lBaaSHandler,
                lBaaSPoolHandler, lBaaSPoolMemberHandler, neutronL3Adapter, distributedArpService);
        trackService(context, INeutronSubnetCRUD.class, lBaaSHandler, lBaaSPoolHandler, lBaaSPoolMemberHandler,
                securityServices, neutronL3Adapter);
        trackService(context, INeutronPortCRUD.class, tenantNetworkManager, lBaaSHandler, lBaaSPoolHandler,
                lBaaSPoolMemberHandler, securityServices, neutronL3Adapter, distributedArpService);
        trackService(context, INeutronFloatingIPCRUD.class, neutronL3Adapter);
        trackService(context, INeutronLoadBalancerCRUD.class, lBaaSHandler, lBaaSPoolHandler, lBaaSPoolMemberHandler);
        trackService(context, INeutronLoadBalancerPoolCRUD.class, lBaaSHandler, lBaaSPoolMemberHandler);
        trackService(context, LoadBalancerProvider.class, lBaaSHandler, lBaaSPoolHandler, lBaaSPoolMemberHandler);
        trackService(context, ArpProvider.class, neutronL3Adapter, distributedArpService);
        trackService(context, InboundNatProvider.class, neutronL3Adapter);
        trackService(context, OutboundNatProvider.class, neutronL3Adapter);
        trackService(context, RoutingProvider.class, neutronL3Adapter);
        trackService(context, L3ForwardingProvider.class, neutronL3Adapter);
        trackService(context, GatewayMacResolver.class, neutronL3Adapter);
        trackService(context, IngressAclProvider.class, securityServices);
        trackService(context, EgressAclProvider.class, securityServices);
        trackService(context, IcmpEchoProvider.class, neutronL3Adapter);

        // We no longer need to track the services, avoid keeping references around
        servicesAndRegistrations.clear();
    }

    private void registerCRUDServiceProviders(BundleContext context) {
        LOG.debug("Registering CRUD service providers");
        NeutronRouterInterface.registerNewInterface(context, dataBroker, translatorCRUDRegistrations);
        NeutronPortInterface.registerNewInterface(context, dataBroker, translatorCRUDRegistrations);
        NeutronSubnetInterface.registerNewInterface(context, dataBroker, translatorCRUDRegistrations);
        NeutronNetworkInterface.registerNewInterface(context, dataBroker, translatorCRUDRegistrations);
        NeutronSecurityGroupInterface.registerNewInterface(context, dataBroker, translatorCRUDRegistrations);
        NeutronSecurityRuleInterface.registerNewInterface(context, dataBroker, translatorCRUDRegistrations);
        NeutronFirewallInterface.registerNewInterface(context, dataBroker, translatorCRUDRegistrations);
        NeutronFirewallPolicyInterface.registerNewInterface(context, dataBroker, translatorCRUDRegistrations);
        NeutronFirewallRuleInterface.registerNewInterface(context, dataBroker, translatorCRUDRegistrations);
        NeutronLoadBalancerInterface.registerNewInterface(context, dataBroker, translatorCRUDRegistrations);
        NeutronLoadBalancerPoolInterface.registerNewInterface(context, dataBroker, translatorCRUDRegistrations);
        NeutronLoadBalancerListenerInterface.registerNewInterface(context, dataBroker, translatorCRUDRegistrations);
        NeutronLoadBalancerHealthMonitorInterface.registerNewInterface(context, dataBroker, translatorCRUDRegistrations);
        NeutronLoadBalancerPoolMemberInterface.registerNewInterface(context, dataBroker, translatorCRUDRegistrations);
        NeutronFloatingIPInterface.registerNewInterface(context, dataBroker, translatorCRUDRegistrations);
    }

    private void trackService(BundleContext context, final Class<?> clazz, final ConfigInterface... dependents) {
        @SuppressWarnings("unchecked")
        ServiceTracker tracker = new ServiceTracker(context, clazz, null) {
            @Override
            public Object addingService(ServiceReference reference) {
                LOG.info("addingService " + clazz.getName());
                Object service = context.getService(reference);
                if (service != null) {
                    for (ConfigInterface dependent : dependents) {
                        dependent.setDependencies(service);
                    }
                }
                return service;
            }
        };
        tracker.open();
    }

    private void registerAbstractHandlerService(BundleContext context, Class[] interfaces,
                                                AbstractEvent.HandlerType handlerType, AbstractHandler handler) {
        Dictionary<String, Object> properties = new Hashtable<>();
        properties.put(Constants.EVENT_HANDLER_TYPE_PROPERTY, handlerType);
        String[] interfaceNames = new String[interfaces.length + 1];
        for (int i = 0; i < interfaces.length; i++) {
            interfaceNames[i] = interfaces[i].getName();
        }
        interfaceNames[interfaces.length] = AbstractHandler.class.getName();
        registerService(context, interfaceNames, properties, handler);
    }


    @Override
    public void stop(BundleContext context) throws Exception {
        LOG.info("ConfigActivator stop");
        // ServiceTrackers and services are already released when bundle stops,
        // so we don't need to close the trackers or unregister the services
    }

    private ServiceRegistration<?> registerService(BundleContext bundleContext, String[] interfaces,
                                                   Dictionary<String, Object> properties, Object impl) {
        ServiceRegistration serviceRegistration = bundleContext.registerService(interfaces, impl, properties);
        if (serviceRegistration == null) {
            LOG.warn("Service registration for {} failed to return a ServiceRegistration instance", impl.getClass());
        }
        servicesAndRegistrations.add(Pair.of(impl, serviceRegistration));
        return serviceRegistration;
    }

    public void setConntrackEnabled(boolean conntrackEnabled) {
        this.conntrackEnabled = conntrackEnabled;
    }

    public void setIntBridgeGenMac(boolean intBridgeGenMac) {
        this.intBridgeGenMac = intBridgeGenMac;
    }
}
