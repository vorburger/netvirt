/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.bgpmanager;

import com.google.common.base.Optional;

import java.util.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.*;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.opendaylight.netvirt.bgpmanager.commands.ClearBgpCli;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.netvirt.bgpmanager.thrift.client.BgpRouter;
import org.opendaylight.netvirt.bgpmanager.thrift.client.BgpRouterException;
import org.opendaylight.netvirt.bgpmanager.thrift.client.BgpSyncHandle;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.Routes;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.Update;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.af_afi;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.af_safi;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.qbgpConstants;
import org.opendaylight.netvirt.bgpmanager.thrift.server.BgpThriftService;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.*;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.*;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.neighbors.*;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BgpConfigurationManager {
    private static final Logger LOG =
    LoggerFactory.getLogger(BgpConfigurationManager.class);
    private static DataBroker broker;
    private static FibDSWriter fib;
    private boolean restarting = false;
    private static Bgp config;
    private static BgpRouter bgpRouter;
    private static BgpThriftService updateServer;

    private static final String DEF_LOGFILE = "/var/log/bgp_debug.log";
    private static final String DEF_LOGLEVEL = "errors";
    private static final String UPDATE_PORT = "bgp.thrift.service.port";
    private static final String CONFIG_HOST = "vpnservice.bgpspeaker.host.name";
    private static final String CONFIG_PORT = "vpnservice.bgpspeaker.thrift.port";
    private static final String DEF_UPORT = "7744";
    private static final String DEF_CHOST = "127.0.0.1";
    private static final String DEF_CPORT = "7644";
    private static final String SDNC_BGP_MIP = "sdnc_bgp_mip";
    private static final String CLUSTER_CONF_FILE = "/cluster/etc/cluster.conf";
    private static final Timer ipActivationCheckTimer = new Timer();

    // to have stale FIB map (RD, Prefix)
    //  number of seconds wait for route sync-up between ODL and BGP.
    private static final int BGP_RESTART_ROUTE_SYNC_SEC = 360;

    static String odlThriftIp = "127.0.0.1";
    private static String cHostStartup; 
    private static String cPortStartup; 
    private static CountDownLatch initer = new CountDownLatch(1);
    //static IITMProvider itmProvider;
    public static BgpManager bgpManager;
    //map<rd, map<prefix/len, nexthop/label>>
    private static Map<String, Map<String, String>> staledFibEntriesMap = new ConcurrentHashMap<>();

    private static final Class[] reactors =  
    {
        ConfigServerReactor.class, AsIdReactor.class,
        GracefulRestartReactor.class, LoggingReactor.class,
        NeighborsReactor.class, UpdateSourceReactor.class,
        EbgpMultihopReactor.class, AddressFamiliesReactor.class,
        NetworksReactor.class, VrfsReactor.class, BgpReactor.class
    };
    
    private ListenerRegistration<DataChangeListener>[] registrations;

    private Object createListener(Class<?> cls) {
        Constructor<?> ctor;
        Object obj = null;

        try {
            ctor= cls.getConstructor(BgpConfigurationManager.class);
            obj =  ctor.newInstance(this);
        } catch (Exception e) {
            LOG.error("Failed to create listener object", e);
        }
        return obj;
    }

    private void registerCallbacks() {
        String emsg = "Failed to register listener";
        registrations = (ListenerRegistration<DataChangeListener>[])
        new ListenerRegistration[reactors.length];
        InstanceIdentifier<?> iid = InstanceIdentifier.create(Bgp.class);
        for (int i = 0; i < reactors.length; i++) {
            DataChangeListener dcl = 
            (DataChangeListener) createListener(reactors[i]);
            String dclName = dcl.getClass().getName();
            try {
                registrations[i] =  broker.registerDataChangeListener(
                                      LogicalDatastoreType.CONFIGURATION,
                                      iid, dcl, DataChangeScope.SUBTREE);
            } catch (Exception e) {
                LOG.error(emsg, e);
                throw new IllegalStateException(emsg+" "+dclName, e);
            }
        }
    }

    public void close() {
        if (updateServer != null) {
            updateServer.stop();
        } 
    }

    private boolean configExists() {
        InstanceIdentifier.InstanceIdentifierBuilder<Bgp> iib =
        InstanceIdentifier.builder(Bgp.class);
        InstanceIdentifier<Bgp> iid = iib.build();
        Optional<Bgp> b = BgpUtil.read(broker, 
        LogicalDatastoreType.CONFIGURATION, iid);
        return b.isPresent();
    }

    private String getProperty(String var, String def) {
        Bundle b = FrameworkUtil.getBundle(BgpManager.class);
        if (b == null) {
            return def;
        }
        BundleContext context = b.getBundleContext();
        if (context == null) {
            return def;
        }
        String s = context.getProperty(var);
        return (s == null ? def : s);
    }

    public BgpConfigurationManager(BgpManager bgpMgr) {
        broker = bgpMgr.getBroker();
        fib = bgpMgr.getFibWriter();
        //itmProvider = bgpMgr.getItmProvider();
        // there must be a good way to detect that we're restarting.
        // but for now infer it from the existance of config
        restarting = configExists();
        bgpManager = bgpMgr;
        bgpRouter = BgpRouter.getInstance();
        String uPort = getProperty(UPDATE_PORT, DEF_UPORT); 
        cHostStartup = getProperty(CONFIG_HOST, DEF_CHOST);
        cPortStartup = getProperty(CONFIG_PORT, DEF_CPORT);
        VtyshCli.setHostAddr(cHostStartup);
        ClearBgpCli.setHostAddr(cHostStartup);
        LOG.info("UpdateServer at localhost:"+uPort+" ConfigServer at "
                 +cHostStartup+":"+cPortStartup);
        updateServer = new BgpThriftService(Integer.parseInt(uPort), bgpMgr);
        updateServer.start();
        readOdlThriftIpForBgpCommunication();
        registerCallbacks();

        // this shouldn't be done. config client must connect in 
        // response to config; but connecting at startup to a default
        // host is legacy behavior. 
        if (!restarting) {
            bgpRouter.connect(cHostStartup, Integer.parseInt(cPortStartup)); 
        }
        LOG.info("BGP Configuration manager initialized");
        initer.countDown();
    }

    public Bgp get() {
        return config;
    }

    private static final String addWarn = 
              "Config store updated; undo with Delete if needed.";
    private static final String delWarn = 
              "Config store updated; undo with Add if needed.";
    private static final String updWarn =
              "Update operation not supported; Config store updated;"
               +" restore with another Update if needed.";

    public class ConfigServerReactor 
    extends AbstractDataChangeListener<ConfigServer> 
    implements AutoCloseable {
        private static final String yangObj = "config-server ";
        public ConfigServerReactor() {
            super(ConfigServer.class);
        }

        protected synchronized void 
        add(InstanceIdentifier<ConfigServer> iid, ConfigServer val) {
            LOG.debug("received bgp connect config host {}", val.getHost().getValue());
            try {
                initer.await();
            } catch (Exception e) {
            }
            LOG.debug("issueing bgp router connect to host {}", val.getHost().getValue());
            synchronized(BgpConfigurationManager.this) {
                boolean res = bgpRouter.connect(val.getHost().getValue(), 
                                                val.getPort().intValue());
                if (!res) {
                    LOG.error(yangObj + "Add failed; "+addWarn);
                }
            }
        }

        protected synchronized void 
        remove(InstanceIdentifier<ConfigServer> iid, ConfigServer val) {
            LOG.debug("received bgp disconnect");
            synchronized(BgpConfigurationManager.this) {
                bgpRouter.disconnect();
            }
        }
                          
        protected void update(InstanceIdentifier<ConfigServer> iid,
                              ConfigServer oldval, ConfigServer newval) {
            LOG.error(yangObj + updWarn);
        }

        public void close() {
            int i;
            for (i=0 ; i < reactors.length ; i++) {
                if (reactors[i] == ConfigServerReactor.class) {
                    break;
                }
            }
            registrations[i].close();
        }
    }

    private BgpRouter getClient(String yangObj) {
        if (bgpRouter == null) {
            LOG.warn(yangObj+": configuration received when BGP is inactive");
        }
        return bgpRouter;
    } 

    public class AsIdReactor 
    extends AbstractDataChangeListener<AsId> 
    implements AutoCloseable {

        private static final String yangObj = "as-id ";

        public AsIdReactor() {
            super(AsId.class);
        }

        protected synchronized void 
        add(InstanceIdentifier<AsId> iid, AsId val) {
            LOG.debug("received add router config asNum {}", val.getLocalAs().intValue());
            synchronized(BgpConfigurationManager.this) {
                BgpRouter br = getClient(yangObj);
                if (br == null) {
                    return;
                }
                int asNum = val.getLocalAs().intValue();
                Ipv4Address routerId = val.getRouterId();
                Long spt = val.getStalepathTime();
                Boolean afb = val.isAnnounceFbit();
                String rid = (routerId == null) ? "" : routerId.getValue();
                int stalepathTime = (spt == null) ? 90 : spt.intValue(); 
                boolean announceFbit = afb != null && afb.booleanValue();
                try {
                    br.startBgp(asNum, rid, stalepathTime, announceFbit); 
                    if (bgpManager.getBgpCounters() == null) {
                        bgpManager.startBgpCountersTask();
                    }
                } catch (BgpRouterException bre) {
                    if (bre.getErrorCode() == BgpRouterException.BGP_ERR_ACTIVE) {
                        LOG.error(yangObj+"Add requested when BGP is already active");
                    } else {
                        LOG.error(yangObj+"Add received exception: \"" 
                                  +bre+"\"; "+addWarn);
                    }
                } catch (Exception e) {
                    LOG.error(yangObj+"Add received exception: \""+e+"\"; "+addWarn);
                }
            }
        } 

        protected synchronized void 
        remove(InstanceIdentifier<AsId> iid, AsId val) {
            LOG.debug("received delete router config asNum {}", val.getLocalAs().intValue());
            synchronized(BgpConfigurationManager.this) {
                BgpRouter br = getClient(yangObj);
                if (br == null) {
                    return;
                }
                int asNum = val.getLocalAs().intValue(); 
                try {
                    br.stopBgp(asNum);
                } catch (Exception e) {
                    LOG.error(yangObj+" Delete received exception:  \""+e+"\"; "+delWarn);
                }
                if (bgpManager.getBgpCounters() != null) {
                    bgpManager.stopBgpCountersTask();
                }
            }
        }
                          
        protected void update(InstanceIdentifier<AsId> iid,
                              AsId oldval, AsId newval) {
            LOG.error(yangObj + updWarn);
        }

        public void close() {
            int i;
            for (i=0 ; i < reactors.length ; i++) {
                if (reactors[i] == AsIdReactor.class) {
                    break;
                }
            }
            registrations[i].close();
        }
    }

    public class GracefulRestartReactor 
    extends AbstractDataChangeListener<GracefulRestart> 
    implements AutoCloseable {

        private static final String yangObj = "graceful-restart ";

        public GracefulRestartReactor() {
            super(GracefulRestart.class);
        }

        protected synchronized void
        add(InstanceIdentifier<GracefulRestart> iid, GracefulRestart val) {
            synchronized(BgpConfigurationManager.this) {
                BgpRouter br = getClient(yangObj);
                if (br == null) {
                    return;
                }
                try {
                    br.addGracefulRestart(val.getStalepathTime().intValue());
                } catch (Exception e) {
                    LOG.error(yangObj+"Add received exception: \""+e+"\"; "+addWarn);
                }
            }
        }

        protected synchronized void 
        remove(InstanceIdentifier<GracefulRestart> iid, GracefulRestart val) {
            LOG.debug("received delete GracefulRestart config val {}", val.getStalepathTime().intValue());
            synchronized(BgpConfigurationManager.this) {
                BgpRouter br = getClient(yangObj);
                if (br == null) {
                    return;
                }
                try {
                    br.delGracefulRestart();
                } catch (Exception e) {
                    LOG.error(yangObj+" Delete received exception:  \""+e+"\"; "
                              +delWarn);
                }
            }
        }
                          
        protected void update(InstanceIdentifier<GracefulRestart> iid,
                              GracefulRestart oldval, GracefulRestart newval) {
        	LOG.debug("received update GracefulRestart config val {}", newval.getStalepathTime().intValue());
            synchronized(BgpConfigurationManager.this) {
                BgpRouter br = getClient(yangObj);
                if (br == null) {
                    return;
                }
                try {
                    br.addGracefulRestart(newval.getStalepathTime().intValue());
                } catch (Exception e) {
                    LOG.error(yangObj+"update received exception: \""+e+"\"; "+addWarn);
                }
            }
        }

        public void close() {
            int i;
            for (i=0 ; i < reactors.length ; i++) {
                if (reactors[i] == GracefulRestartReactor.class) {
                    break;
                }
            }
            registrations[i].close();
        }
    }

    public class LoggingReactor 
    extends AbstractDataChangeListener<Logging> 
    implements AutoCloseable {

        private static final String yangObj = "logging ";

        public LoggingReactor() {
            super(Logging.class);
        }

        protected synchronized void
        add(InstanceIdentifier<Logging> iid, Logging val) {
            synchronized(BgpConfigurationManager.this) {
                BgpRouter br = getClient(yangObj);
                if (br == null) {
                    return;
                }
                try {
                    br.setLogging(val.getFile(),val.getLevel());
                } catch (Exception e) {
                    LOG.error(yangObj+"Add received exception: \""+e+"\"; "
                              +addWarn);
                }
            }
        }

        protected synchronized void 
        remove(InstanceIdentifier<Logging> iid, Logging val) {
            LOG.debug("received remove Logging config val {}", val.getLevel());
            synchronized(BgpConfigurationManager.this) {
                BgpRouter br = getClient(yangObj);
                if (br == null) {
                    return;
                }
                try {
                    br.setLogging(DEF_LOGFILE, DEF_LOGLEVEL);
                } catch (Exception e) {
                    LOG.error(yangObj+" Delete received exception:  \""+e+"\"; "
                              +delWarn);
                }
            }
        }
                          
        protected void update(InstanceIdentifier<Logging> iid,
                              Logging oldval, Logging newval) {
            synchronized(BgpConfigurationManager.this) {
                BgpRouter br = getClient(yangObj);
                if (br == null) {
                    return;
                }
                try {
                    br.setLogging(newval.getFile(),newval.getLevel());
                } catch (Exception e) {
                    LOG.error(yangObj+"newval received exception: \""+e+"\"; "
                              +addWarn);
                }
            }
        }

        public void close() {
            int i;
            for (i=0 ; i < reactors.length ; i++) {
                if (reactors[i] == LoggingReactor.class) {
                    break;
                }
            }
            registrations[i].close();
        }
    }

    public class NeighborsReactor 
    extends AbstractDataChangeListener<Neighbors> 
    implements AutoCloseable {

        private static final String yangObj = "neighbors ";

        public NeighborsReactor() {
            super(Neighbors.class);
        }

        protected synchronized void 
        add(InstanceIdentifier<Neighbors> iid, Neighbors val) {
            LOG.debug("received add Neighbors config val {}", val.getAddress().getValue());
            synchronized(BgpConfigurationManager.this) {
                BgpRouter br = getClient(yangObj);
                if (br == null) {
                    return;
                }
                String peerIp = val.getAddress().getValue();
                int as = val.getRemoteAs().intValue();
                try {
                    //itmProvider.buildTunnelsToDCGW(new IpAddress(peerIp.toCharArray()));
                    br.addNeighbor(peerIp, as);
            
                } catch (Exception e) {
                    LOG.error(yangObj+"Add received exception: \""+e+"\"; "
                              +addWarn);
                }
            }
        }

        protected synchronized void 
        remove(InstanceIdentifier<Neighbors> iid, Neighbors val) {
            LOG.debug("received remove Neighbors config val {}", val.getAddress().getValue());
            synchronized(BgpConfigurationManager.this) {
                BgpRouter br = getClient(yangObj);
                if (br == null) {
                    return;
                }
                String peerIp = val.getAddress().getValue();
                try {
                    //itmProvider.deleteTunnelsToDCGW(new IpAddress(val.getAddress().getValue().toCharArray()));
                    br.delNeighbor(peerIp);
                } catch (Exception e) {
                    LOG.error(yangObj+" Delete received exception:  \""+e+"\"; "
                              +delWarn);
                }
            }
        }
                          
        protected void update(InstanceIdentifier<Neighbors> iid,
                              Neighbors oldval, Neighbors newval) {
            //purposefully nothing to do.
        }

        public void close() {
            int i;
            for (i=0 ; i < reactors.length ; i++) {
                if (reactors[i] == NeighborsReactor.class) {
                    break;
                }
            }
            registrations[i].close();
        }
    }

    public class EbgpMultihopReactor 
    extends AbstractDataChangeListener<EbgpMultihop> 
    implements AutoCloseable {

        private static final String yangObj = "ebgp-multihop ";

        public EbgpMultihopReactor() {
            super(EbgpMultihop.class);
        }

        protected synchronized void 
        add(InstanceIdentifier<EbgpMultihop> iid, EbgpMultihop val) {
            LOG.debug("received add EbgpMultihop config val {}", val.getPeerIp().getValue());  
            synchronized(BgpConfigurationManager.this) {
                BgpRouter br = getClient(yangObj);
                if (br == null) {
                    return;
                }
                String peerIp = val.getPeerIp().getValue();
                try {
                    br.addEbgpMultihop(peerIp, val.getNhops().intValue()); 
                } catch (Exception e) {
                    LOG.error(yangObj+"Add received exception: \""+e+"\"; "
                              +addWarn);
                }
            }
        }

        protected synchronized void 
        remove(InstanceIdentifier<EbgpMultihop> iid, EbgpMultihop val) {
            LOG.debug("received remove EbgpMultihop config val {}", val.getPeerIp().getValue());
            synchronized(BgpConfigurationManager.this) {
                BgpRouter br = getClient(yangObj);
                if (br == null) {
                    return;
                }
                String peerIp = val.getPeerIp().getValue();
                try {
                    br.delEbgpMultihop(peerIp);
                } catch (Exception e) {
                    LOG.error(yangObj+" Delete received exception:  \""+e+"\"; "
                              +delWarn);
                }
            }
        }
                          
        protected void update(InstanceIdentifier<EbgpMultihop> iid,
                              EbgpMultihop oldval, EbgpMultihop newval) {
            LOG.error(yangObj + updWarn);
        }

        public void close() {
            int i;
            for (i=0 ; i < reactors.length ; i++) {
                if (reactors[i] == EbgpMultihopReactor.class) {
                    break;
                }
            }
            registrations[i].close();
        }
    }

    public class UpdateSourceReactor 
    extends AbstractDataChangeListener<UpdateSource> 
    implements AutoCloseable {

        private static final String yangObj = "update-source ";

        public UpdateSourceReactor() {
            super(UpdateSource.class);
        }

        protected synchronized void 
        add(InstanceIdentifier<UpdateSource> iid, UpdateSource val) {
            LOG.debug("received add UpdateSource config val {}", val.getSourceIp().getValue());
            synchronized(BgpConfigurationManager.this) {
                BgpRouter br = getClient(yangObj);
                if (br == null) {
                    return;
                }
                String peerIp = val.getPeerIp().getValue();
                try {
                    br.addUpdateSource(peerIp, val.getSourceIp().getValue()); 
                } catch (Exception e) {
                    LOG.error(yangObj+"Add received exception: \""+e+"\"; "
                              +addWarn);
                }
            }
        }

        protected synchronized void 
        remove(InstanceIdentifier<UpdateSource> iid, UpdateSource val) {
            LOG.debug("received remove UpdateSource config val {}", val.getSourceIp().getValue());
            synchronized(BgpConfigurationManager.this) {
                BgpRouter br = getClient(yangObj);
                if (br == null) {
                    return;
                }
                String peerIp = val.getPeerIp().getValue();
                try {
                    br.delUpdateSource(peerIp);
                } catch (Exception e) {
                    LOG.error(yangObj+" Delete received exception:  \""+e+"\"; "
                              +delWarn);
                }
            }
        }
                          
        protected void update(InstanceIdentifier<UpdateSource> iid,
                              UpdateSource oldval, UpdateSource newval) {
            LOG.error(yangObj + updWarn);
        }

        public void close() {
            int i;
            for (i=0 ; i < reactors.length ; i++) {
                if (reactors[i] == UpdateSourceReactor.class) {
                    break;
                }
            }
            registrations[i].close();
        }
    }

    public class AddressFamiliesReactor 
    extends AbstractDataChangeListener<AddressFamilies> 
    implements AutoCloseable {

        private static final String yangObj = "address-families ";

        public AddressFamiliesReactor() {
            super(AddressFamilies.class);
        }

        protected synchronized void 
        add(InstanceIdentifier<AddressFamilies> iid, AddressFamilies val) {
            LOG.debug("received add AddressFamilies config val {}", val.getPeerIp().getValue());
            synchronized(BgpConfigurationManager.this) {
                BgpRouter br = getClient(yangObj);
                if (br == null) {
                    return;
                }
                String peerIp = val.getPeerIp().getValue();
                af_afi afi = af_afi.findByValue(val.getAfi().intValue());
                af_safi safi = af_safi.findByValue(val.getSafi().intValue());
                try {
                    br.addAddressFamily(peerIp, afi, safi); 
                } catch (Exception e) {
                    LOG.error(yangObj+"Add received exception: \""+e+"\"; "
                              +addWarn);
                }
            }
        }

        protected synchronized void 
        remove(InstanceIdentifier<AddressFamilies> iid, AddressFamilies val) {
            LOG.debug("received remove AddressFamilies config val {}", val.getPeerIp().getValue());
            synchronized(BgpConfigurationManager.this) {
                BgpRouter br = getClient(yangObj);
                if (br == null) {
                    return;
                }
                String peerIp = val.getPeerIp().getValue();
                af_afi afi = af_afi.findByValue(val.getAfi().intValue());
                af_safi safi = af_safi.findByValue(val.getSafi().intValue());
                try {
                    br.delAddressFamily(peerIp, afi, safi);
                } catch (Exception e) {
                    LOG.error(yangObj+" Delete received exception:  \""+e+"\"; "
                              +delWarn);
                }
            }
        }
                          
        protected void update(InstanceIdentifier<AddressFamilies> iid,
                              AddressFamilies oldval, AddressFamilies newval) {
            LOG.error(yangObj + updWarn);
        }

        public void close() {
            int i;
            for (i=0 ; i < reactors.length ; i++) {
                if (reactors[i] == AddressFamiliesReactor.class) {
                    break;
                }
            }
            registrations[i].close();
        }
    }

    public class NetworksReactor 
    extends AbstractDataChangeListener<Networks> 
    implements AutoCloseable {

        private static final String yangObj = "networks ";

        public NetworksReactor() {
            super(Networks.class);
        }

        protected synchronized void 
        add(InstanceIdentifier<Networks> iid, Networks val) {
            LOG.debug("received add Networks config val {}", val.getPrefixLen());
            synchronized(BgpConfigurationManager.this) {
                BgpRouter br = getClient(yangObj);
                if (br == null) {
                    return;
                }
                String rd = val.getRd();
                String pfxlen = val.getPrefixLen();
                String nh = val.getNexthop().getValue();
                Long label = val.getLabel();
                int lbl = (label == null) ? qbgpConstants.LBL_NO_LABEL
                                            : label.intValue();
                try {
                    br.addPrefix(rd, pfxlen, nh, lbl); 
                } catch (Exception e) {
                    LOG.error(yangObj+"Add received exception: \""+e+"\"; "+addWarn);
                }
            }
        }

        protected synchronized void 
        remove(InstanceIdentifier<Networks> iid, Networks val) {
            LOG.debug("received remove Networks config val {}", val.getPrefixLen());
            synchronized(BgpConfigurationManager.this) {
                BgpRouter br = getClient(yangObj);
                if (br == null) {
                    return;
                }
                String rd = val.getRd();
                String pfxlen = val.getPrefixLen();
                Long label = val.getLabel();
                int lbl = (label == null) ? 0 : label.intValue();
                if (rd == null && lbl > 0) {
                    //LU prefix is being deleted. 
                    rd = Integer.toString(lbl);
                }
                try {
                    br.delPrefix(rd, pfxlen);
                } catch (Exception e) {
                    LOG.error(yangObj+" Delete received exception:  \""+e+"\"; "
                              +delWarn);
                }
            }
        }
                          
        protected void update(InstanceIdentifier<Networks> iid,
                              Networks oldval, Networks newval) {
            LOG.error(yangObj + updWarn);
        }

        public void close() {
            int i;
            for (i=0 ; i < reactors.length ; i++) {
                if (reactors[i] == NetworksReactor.class) {
                    break;
                }
            }
            registrations[i].close();
        }
    }

    public class VrfsReactor 
    extends AbstractDataChangeListener<Vrfs> 
    implements AutoCloseable {

        private static final String yangObj = "vrfs ";

        public VrfsReactor() {
            super(Vrfs.class);
        }

        protected synchronized void 
        add(InstanceIdentifier<Vrfs> iid, Vrfs val) {
            LOG.debug("received add Vrfs config val {}", val.getRd());
            synchronized(BgpConfigurationManager.this) {
                BgpRouter br = getClient(yangObj);
                if (br == null) {
                    return;
                }
                try {
                    br.addVrf(val.getRd(), val.getImportRts(), 
                              val.getExportRts()); 
                } catch (Exception e) {
                    LOG.error(yangObj+"Add received exception: \""+e+"\"; "
                              +addWarn);
                }
            }
        }

        protected synchronized void 
        remove(InstanceIdentifier<Vrfs> iid, Vrfs val) {
            LOG.debug("received remove Vrfs config val {}", val.getRd());
            synchronized(BgpConfigurationManager.this) {
                BgpRouter br = getClient(yangObj);
                if (br == null) {
                    return;
                }
                try {
                    br.delVrf(val.getRd());
                } catch (Exception e) {
                    LOG.error(yangObj+" Delete received exception:  \""+e+"\"; "
                              +delWarn);
                }
            }
        }
                          
        protected void update(InstanceIdentifier<Vrfs> iid,
                              Vrfs oldval, Vrfs newval) {
            LOG.error(yangObj + updWarn);
        }

        public void close() {
            int i;
            for (i=0 ; i < reactors.length ; i++) {
                if (reactors[i] == VrfsReactor.class) {
                    break;
                }
            }
            registrations[i].close();
        }
    }

    Future lastCleanupJob;
    AtomicReference<Future> lastCleanupJobReference = new AtomicReference<>();

    AtomicBoolean started = new AtomicBoolean(false);
    public class BgpReactor 
    extends AbstractDataChangeListener<Bgp> 
    implements AutoCloseable {
 
        private static final String yangObj = "Bgp ";

        public BgpReactor() {
            super(Bgp.class);
        }

        protected synchronized void 
        add(InstanceIdentifier<Bgp> iid, Bgp val) {
            LOG.debug("received add Bgp config replaying the config");
            try {
                initer.await();
            } catch (Exception e) {
            }
            synchronized(BgpConfigurationManager.this) {
                config = val;
                if (restarting) {
                    if (isIpAvailable(odlThriftIp)) {
                        bgpRestarted();
                    } else {
                        ipActivationCheckTimer.scheduleAtFixedRate(new TimerTask() {
                            public void run() {
                                if (isIpAvailable(odlThriftIp)) {
                                    bgpRestarted();
                                    ipActivationCheckTimer.cancel();
                                }
                            }
                        }, 10000L, 10000L);
                    }
                }
            }
        }

        protected synchronized void 
        remove(InstanceIdentifier<Bgp> iid, Bgp val) {
            LOG.debug("received remove Bgp config");
            synchronized(BgpConfigurationManager.this) {
                config = null;
            }
        }
                          
        protected void update(InstanceIdentifier<Bgp> iid,
                              Bgp oldval, Bgp newval) {
            synchronized(BgpConfigurationManager.this) {
                config = newval;
            }
        }

        public void close() {
            int i;
            for (i=0 ; i < reactors.length ; i++) {
                if (reactors[i] == BgpReactor.class) {
                    break;
                }
            }
            registrations[i].close();
        }
    }
    
    public void readOdlThriftIpForBgpCommunication() {
        File f = new File(CLUSTER_CONF_FILE);
        if (!f.exists()) {
            odlThriftIp = "127.0.0.1";
            return;
        }
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(
                    new FileInputStream(f)));
            String line = br.readLine();
            while (line != null) {
                if (line.contains(SDNC_BGP_MIP)) {
                    line = line.trim();
                    odlThriftIp = line.substring(line.lastIndexOf(" ")+1);
                    break;
                }
                line = br.readLine();
            }
        } catch (Exception e) {
        } finally {
            try {br.close();} catch (Exception ignore){}
        }
    }
    
    public boolean isIpAvailable(String odlip) {
        
        try {
            if (odlip != null) {
                if ("127.0.0.1".equals(odlip)) {
                    return true;
                }
                Enumeration e = NetworkInterface.getNetworkInterfaces();
                while(e.hasMoreElements())
                {
                    NetworkInterface n = (NetworkInterface) e.nextElement();
                    Enumeration ee = n.getInetAddresses();
                    while (ee.hasMoreElements())
                    {
                        InetAddress i = (InetAddress) ee.nextElement();
                        if (odlip.equals(i.getHostAddress())) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
        }
        return false;
    }

    public void bgpRestarted() {
        /*
         * If there a thread which in the process of stale cleanup, cancel it
         * and start a new thread (to avoid processing same again).
         */
        if (lastCleanupJobReference.get() != null) {
            lastCleanupJobReference.get().cancel(true);
            lastCleanupJobReference.set(null);
        }
        Runnable task = new Runnable() {
            @Override
            public void run() {
                try {
                    long startTime = System.currentTimeMillis();
                    LOG.error("started creating stale fib  map ");
                    createStaleFibMap();
                    long endTime = System.currentTimeMillis();
                    LOG.error("took {} msecs for stale fib map creation ", endTime - startTime);
                    LOG.error("started bgp config replay ");
                    startTime = endTime;
                    replay();
                    endTime = System.currentTimeMillis();
                    LOG.error("took {} msecs for bgp replay ", endTime - startTime);
                    long route_sync_time = BGP_RESTART_ROUTE_SYNC_SEC;
                    try {
                        route_sync_time = bgpManager.getConfig().getGracefulRestart().getStalepathTime();
                    } catch (Exception e) {
                        LOG.error("BGP config/Stale-path time is not set");
                    }
                    Thread.sleep(route_sync_time * 1000L);
                    new RouteCleanup().call();

                } catch (Exception eCancel) {
                    LOG.error("Stale Cleanup Task Cancelled", eCancel);
                }
            }
        };
        lastCleanupJob = executor.submit(task);
        lastCleanupJobReference.set(lastCleanupJob);
    }

    private static void doRouteSync() {
        BgpSyncHandle bsh = BgpSyncHandle.getInstance();
        LOG.debug("Starting BGP route sync");
        try {
            bgpRouter.initRibSync(bsh); 
        } catch (Exception e) {
            LOG.error("Route sync aborted, exception when initialzing: "+e);
            return;
        }
        while (bsh.getState() != bsh.DONE) {
            Routes routes = null;
            try {
                routes = bgpRouter.doRibSync(bsh);
            } catch (Exception e) {
                LOG.error("Route sync aborted, exception when syncing: "+e);
                return;
            }
            Iterator<Update> updates = routes.getUpdatesIterator();
            while (updates.hasNext()) {
                Update u = updates.next();
                Map<String, Map<String, String>> stale_fib_rd_map = BgpConfigurationManager.getStaledFibEntriesMap();
                String rd = u.getRd();
                String nexthop = u.getNexthop();
                int label = u.getLabel();
                String prefix = u.getPrefix();
                int plen = u.getPrefixlen();
                onUpdatePushRoute(rd, prefix, plen, nexthop, label);
            }
        }
        try {
            LOG.debug("Ending BGP route-sync");
            bgpRouter.endRibSync(bsh);
        } catch (Exception e) {
        }
    }

    /* onUpdatePushRoute
     * Get Stale fib map, and compare current route/fib entry.
     *  - Entry compare shall include NextHop, Label.
     *  - If entry matches: delete from STALE Map. NO Change to FIB Config DS.
     *  - If entry nor found, add to FIB Config DS.
     *  - If entry found, but either Label/NextHop doesnt match.
     *      - Update FIB Config DS with modified values.
     *      - delete from Stale Map.
     */
    public static void onUpdatePushRoute(String rd, String prefix, int plen,
                                  String nexthop, int label) {
        Map<String, Map<String, String>> stale_fib_rd_map = BgpConfigurationManager.getStaledFibEntriesMap();
        boolean addroute = false;
        if (!stale_fib_rd_map.isEmpty()) {
            // restart Scenario, as MAP is not empty.
            Map<String, String> map = stale_fib_rd_map.get(rd);
            if (map !=null) {
                String nexthoplabel = map.get(prefix + "/" + plen);
                if (null == nexthoplabel) {
                    // New Entry, which happend to be added during restart.
                    addroute = true;
                } else {
                    map.remove(prefix + "/" + plen);
                    if (isRouteModified(nexthop, label, nexthoplabel)) {
                        LOG.debug("Route add ** {} ** {}/{} ** {} ** {} ", rd, prefix,
                                plen, nexthop, label);
                        // Existing entry, where in Nexthop/Label got modified during restart
                        addroute = true;
                    }
                }
            }
        } else {
            LOG.debug("Route add ** {} ** {}/{} ** {} ** {} ", rd, prefix,
                    plen, nexthop, label);
            addroute = true;
        }
        if (addroute) {
            fib.addFibEntryToDS(rd, prefix + "/" + plen,
                    nexthop, label);
        }
    }

    private static boolean isRouteModified(String nexthop, int label, String nexthoplabel) {
        return !nexthoplabel.isEmpty() && !nexthoplabel.equals(nexthop+"/"+label);
    }

    static private void replayNbrConfig(List<Neighbors> n, BgpRouter br) { 
        for (Neighbors nbr : n) {
            try {
                br.addNeighbor(nbr.getAddress().getValue(),
                               nbr.getRemoteAs().intValue());
                //itmProvider.buildTunnelsToDCGW(new IpAddress(nbr.getAddress().getValue().toCharArray()));
            } catch (Exception e) {
                LOG.error("Replay:addNbr() received exception: \""+e+"\"");
                continue;
            }
            EbgpMultihop en = nbr.getEbgpMultihop();
            if (en != null) {
                try {
                    br.addEbgpMultihop(en.getPeerIp().getValue(), 
                                       en.getNhops().intValue()); 
                } catch (Exception e) {
                    LOG.error("Replay:addEBgp() received exception: \""+e+"\"");
                }
            }
            UpdateSource us = nbr.getUpdateSource();
            if (us != null) {
                try {
                    br.addUpdateSource(us.getPeerIp().getValue(),
                                       us.getSourceIp().getValue());
                } catch (Exception e) {
                    LOG.error("Replay:addUS() received exception: \""+e+"\"");
                }
            }
            List<AddressFamilies> afs = nbr.getAddressFamilies();
            if (afs != null) {
                for (AddressFamilies af : afs) {
                    af_afi afi = af_afi.findByValue(af.getAfi().intValue());
                    af_safi safi = af_safi.findByValue(af.getSafi().intValue());
                    try {
                        br.addAddressFamily(af.getPeerIp().getValue(), afi, safi);
                    } catch (Exception e) {
                        LOG.error("Replay:addAf() received exception: \""+e+"\"");
                    }
                }
            }
        }
    }

    public static String getConfigHost() {
        if (config == null) {
            return cHostStartup;
        }
        ConfigServer ts = config.getConfigServer();
        return (ts == null ? cHostStartup : ts.getHost().getValue());
    }

    public static int getConfigPort() {
        if (config == null) {
            return Integer.parseInt(cPortStartup);
        }
        ConfigServer ts = config.getConfigServer();
        return (ts == null ? Integer.parseInt(cPortStartup) :
                             ts.getPort().intValue());
    }

    public static synchronized void replay() {
        String host = getConfigHost();
        int port = getConfigPort();
        boolean res = bgpRouter.connect(host, port);
        if (!res) {
            String msg = "Cannot connect to BGP config server at "+host+":"+port;
            if (config != null) {
                msg += "; Configuration Replay aborted";
            }
            LOG.error(msg);
            return;
        }
        if (config == null) {
            return;
        }
        BgpRouter br = bgpRouter; 
        AsId a = config.getAsId();
        if (a == null) {
            return;
        }
        int asNum = a.getLocalAs().intValue();
        Ipv4Address routerId = a.getRouterId();
        Long spt = a.getStalepathTime();
        Boolean afb = a.isAnnounceFbit();
        String rid = (routerId == null) ? "" : routerId.getValue();
        int stalepathTime = (spt == null) ? 90 : spt.intValue(); 
        boolean announceFbit = afb != null && afb.booleanValue();
        try {
            br.startBgp(asNum, rid, stalepathTime, announceFbit); 
        } catch (BgpRouterException bre) {
            if (bre.getErrorCode() == BgpRouterException.BGP_ERR_ACTIVE) {
                doRouteSync();
            } else {
                LOG.error("Replay: startBgp() received exception: \""
                          +bre+"\"; "+addWarn);
            }
        } catch (Exception e) {
            //not unusual. We may have restarted & BGP is already on
            LOG.error("Replay:startBgp() received exception: \""+e+"\"");
        }

        if (bgpManager.getBgpCounters() == null) {
            bgpManager.startBgpCountersTask();
        }
      
        Logging l = config.getLogging();
        if (l != null) {
            try {
                br.setLogging(l.getFile(), l.getLevel());
            } catch (Exception e) {
                LOG.error("Replay:setLogging() received exception: \""+e+"\"");
            }
        }

        GracefulRestart g = config.getGracefulRestart();
        if (g != null) {
            try {
                br.addGracefulRestart(g.getStalepathTime().intValue()); 
            } catch (Exception e) {
                LOG.error("Replay:addGr() received exception: \""+e+"\"");
            }
        }

        List<Neighbors> n = config.getNeighbors();
        if (n != null) {
            replayNbrConfig(n, br);
        }

        List<Vrfs> v = config.getVrfs();
        if (v != null) {
            for (Vrfs vrf : v)  {
                try {
                    br.addVrf(vrf.getRd(), vrf.getImportRts(), 
                    vrf.getExportRts());
                } catch (Exception e) {
                    LOG.error("Replay:addVrf() received exception: \""+e+"\"");
                }
            }
        }

        List<Networks> ln = config.getNetworks();
        if (ln != null) {
            for (Networks net : ln) {
                String rd = net.getRd();
                String pfxlen = net.getPrefixLen();
                String nh = net.getNexthop().getValue();
                Long label = net.getLabel();
                int lbl = (label == null) ? 0 : label.intValue();
                if (rd == null && lbl > 0) {
                    //LU prefix is being deleted. 
                    rd = Integer.toString(lbl);
                }
                try {
                    br.addPrefix(rd, pfxlen, nh, lbl); 
                } catch (Exception e) {
                    LOG.error("Replay:addPfx() received exception: \""+e+"\"");
                }
            }
        }
    }

    private <T extends DataObject> void update(InstanceIdentifier<T> iid, T dto) {
        BgpUtil.update(broker, LogicalDatastoreType.CONFIGURATION, iid, dto);
    }

    private <T extends DataObject> void asyncWrite(InstanceIdentifier<T> iid, T dto) {
        BgpUtil.write(broker,LogicalDatastoreType.CONFIGURATION,iid,dto);
    }

    private <T extends DataObject> void delete(InstanceIdentifier<T> iid) {
        BgpUtil.delete(broker, LogicalDatastoreType.CONFIGURATION, iid);
    } 

    public synchronized void
    startConfig(String bgpHost, int thriftPort) {
        InstanceIdentifier.InstanceIdentifierBuilder<ConfigServer> iib =
            InstanceIdentifier.builder(Bgp.class).child(ConfigServer.class);
        InstanceIdentifier<ConfigServer> iid = iib.build();
        Ipv4Address ipAddr = new Ipv4Address(bgpHost);
        ConfigServer dto  = new ConfigServerBuilder().setHost(ipAddr)
                                            .setPort((long) thriftPort).build();
        update(iid, dto);
    }

    public synchronized void
    startBgp(int as, String routerId, int spt, boolean fbit) {
        Long localAs = (long) as;
        Ipv4Address rid = (routerId == null) ? 
                           null : new Ipv4Address(routerId);
        Long staleTime = (long) spt;
        InstanceIdentifier.InstanceIdentifierBuilder<AsId> iib =
            InstanceIdentifier.builder(Bgp.class).child(AsId.class);
        InstanceIdentifier<AsId> iid = iib.build();
        AsId dto = new AsIdBuilder().setLocalAs(localAs)
                                    .setRouterId(rid)
                                    .setStalepathTime(staleTime)
                                    .setAnnounceFbit(fbit).build();
        update(iid, dto);
    }

    public synchronized void
    addLogging(String fileName, String logLevel) {
        InstanceIdentifier.InstanceIdentifierBuilder<Logging> iib =
            InstanceIdentifier.builder(Bgp.class).child(Logging.class);
        InstanceIdentifier<Logging> iid = iib.build();
        Logging dto = new LoggingBuilder().setFile(fileName)
                                          .setLevel(logLevel).build();
        update(iid, dto);
    }

    public synchronized void
    addGracefulRestart(int staleTime) {
        InstanceIdentifier.InstanceIdentifierBuilder<GracefulRestart> iib = 
            InstanceIdentifier.builder(Bgp.class).child(GracefulRestart.class);
        InstanceIdentifier<GracefulRestart> iid = iib.build();
        GracefulRestart dto = new GracefulRestartBuilder()
                                     .setStalepathTime((long)staleTime).build();
        update(iid, dto);
    }

    public synchronized void
    addNeighbor(String nbrIp, int remoteAs) {
        Ipv4Address nbrAddr = new Ipv4Address(nbrIp);
        Long rAs = (long) remoteAs;
        InstanceIdentifier.InstanceIdentifierBuilder<Neighbors> iib = 
            InstanceIdentifier.builder(Bgp.class)
                              .child(Neighbors.class, new NeighborsKey(nbrAddr));
        InstanceIdentifier<Neighbors> iid = iib.build();
        Neighbors dto = new NeighborsBuilder().setAddress(nbrAddr)
                                              .setRemoteAs(rAs).build();
        update(iid, dto);
    }

    public synchronized void
    addUpdateSource(String nbrIp, String srcIp) {
        Ipv4Address nbrAddr = new Ipv4Address(nbrIp);
        Ipv4Address srcAddr = new Ipv4Address(srcIp);
        InstanceIdentifier.InstanceIdentifierBuilder<UpdateSource> iib = 
            InstanceIdentifier.builder(Bgp.class)
                              .child(Neighbors.class, new NeighborsKey(nbrAddr))
                              .child(UpdateSource.class);
        InstanceIdentifier<UpdateSource> iid = iib.build();
        UpdateSource dto = new UpdateSourceBuilder().setPeerIp(nbrAddr)
                                                  .setSourceIp(srcAddr).build();
        update(iid, dto);
    }

    public synchronized void
    addEbgpMultihop(String nbrIp, int nHops) {
        Ipv4Address nbrAddr = new Ipv4Address(nbrIp);
        InstanceIdentifier.InstanceIdentifierBuilder<EbgpMultihop> iib = 
            InstanceIdentifier.builder(Bgp.class)
                              .child(Neighbors.class, new NeighborsKey(nbrAddr))
                              .child(EbgpMultihop.class);
        InstanceIdentifier<EbgpMultihop> iid = iib.build();
        EbgpMultihop dto = new EbgpMultihopBuilder().setPeerIp(nbrAddr)
                                                 .setNhops((long)nHops).build();
        update(iid, dto);
    }

    public synchronized void
    addAddressFamily(String nbrIp, int afi, int safi) {
        Ipv4Address nbrAddr = new Ipv4Address(nbrIp);
        Long a = (long) afi;
        Long sa = (long) safi;
        InstanceIdentifier.InstanceIdentifierBuilder<AddressFamilies> iib = 
            InstanceIdentifier.builder(Bgp.class)
                              .child(Neighbors.class, new NeighborsKey(nbrAddr))
                   .child(AddressFamilies.class, new AddressFamiliesKey(a, sa));
        InstanceIdentifier<AddressFamilies> iid = iib.build();
        AddressFamilies dto = new AddressFamiliesBuilder().setPeerIp(nbrAddr)
                                                 .setAfi(a).setSafi(sa).build();
        update(iid, dto);
    }

    public synchronized void
    addPrefix(String rd, String pfx, String nh, int lbl) {
        Ipv4Address nexthop = new Ipv4Address(nh);
        Long label = (long) lbl;
        InstanceIdentifier.InstanceIdentifierBuilder<Networks> iib = 
            InstanceIdentifier.builder(Bgp.class)
                              .child(Networks.class, new NetworksKey(pfx, rd));
        InstanceIdentifier<Networks> iid = iib.build();
        Networks dto = new NetworksBuilder().setRd(rd)
                                            .setPrefixLen(pfx)
                                            .setNexthop(nexthop)
                                            .setLabel(label).build();
        update(iid, dto);
    }

    public synchronized void
    addVrf(String rd, List<String> irts, List<String> erts) {
        InstanceIdentifier.InstanceIdentifierBuilder<Vrfs> iib =
            InstanceIdentifier.builder(Bgp.class)
                              .child(Vrfs.class, new VrfsKey(rd));
        InstanceIdentifier<Vrfs> iid = iib.build();
        Vrfs dto = new VrfsBuilder().setRd(rd)
                                    .setImportRts(irts)
                                    .setExportRts(erts).build();

        asyncWrite(iid, dto);
    }

    public synchronized void stopConfig() {
        InstanceIdentifier.InstanceIdentifierBuilder<ConfigServer> iib =
            InstanceIdentifier.builder(Bgp.class).child(ConfigServer.class);
        InstanceIdentifier<ConfigServer> iid = iib.build();
        delete(iid);
    }

    public synchronized void stopBgp() {
        InstanceIdentifier.InstanceIdentifierBuilder<AsId> iib =
            InstanceIdentifier.builder(Bgp.class).child(AsId.class);
        InstanceIdentifier<AsId> iid = iib.build();
        delete(iid);
    }

    public synchronized void delLogging() {
        InstanceIdentifier.InstanceIdentifierBuilder<Logging> iib =
            InstanceIdentifier.builder(Bgp.class).child(Logging.class);
        InstanceIdentifier<Logging> iid = iib.build();
        delete(iid);
    }

    public synchronized void delGracefulRestart() {
        InstanceIdentifier.InstanceIdentifierBuilder<GracefulRestart> iib = 
            InstanceIdentifier.builder(Bgp.class)
                              .child(GracefulRestart.class);
        InstanceIdentifier<GracefulRestart> iid = iib.build();
        delete(iid);
    }

    public synchronized void delNeighbor(String nbrIp) {
        Ipv4Address nbrAddr = new Ipv4Address(nbrIp);
        InstanceIdentifier.InstanceIdentifierBuilder<Neighbors> iib = 
            InstanceIdentifier.builder(Bgp.class)
                              .child(Neighbors.class, new NeighborsKey(nbrAddr));
        InstanceIdentifier<Neighbors> iid = iib.build();
        delete(iid);
    }

    public synchronized void delUpdateSource(String nbrIp) {
        Ipv4Address nbrAddr = new Ipv4Address(nbrIp);
        InstanceIdentifier.InstanceIdentifierBuilder<UpdateSource> iib = 
            InstanceIdentifier.builder(Bgp.class)
                              .child(Neighbors.class, new NeighborsKey(nbrAddr))
                              .child(UpdateSource.class);
        InstanceIdentifier<UpdateSource> iid = iib.build();
        delete(iid);
    }

    public synchronized void delEbgpMultihop(String nbrIp) {
        Ipv4Address nbrAddr = new Ipv4Address(nbrIp);
        InstanceIdentifier.InstanceIdentifierBuilder<EbgpMultihop> iib = 
            InstanceIdentifier.builder(Bgp.class)
                              .child(Neighbors.class, new NeighborsKey(nbrAddr))
                              .child(EbgpMultihop.class);
        InstanceIdentifier<EbgpMultihop> iid = iib.build();
        delete(iid);
    }

    public synchronized void 
    delAddressFamily(String nbrIp, int afi, int safi) {
        Ipv4Address nbrAddr = new Ipv4Address(nbrIp);
        Long a = (long) afi;
        Long sa = (long) safi;
        InstanceIdentifier.InstanceIdentifierBuilder<AddressFamilies> iib = 
            InstanceIdentifier.builder(Bgp.class)
                              .child(Neighbors.class, new NeighborsKey(nbrAddr))
                   .child(AddressFamilies.class, new AddressFamiliesKey(a, sa));
        InstanceIdentifier<AddressFamilies> iid = iib.build();
        delete(iid);
    }

    public synchronized void delPrefix(String rd, String pfx) {
        InstanceIdentifier.InstanceIdentifierBuilder<Networks> iib = 
            InstanceIdentifier.builder(Bgp.class)
                              .child(Networks.class, new NetworksKey(pfx, rd));
        InstanceIdentifier<Networks> iid = iib.build();
        delete(iid);
    }

    public synchronized void delVrf(String rd) {
        InstanceIdentifier.InstanceIdentifierBuilder<Vrfs> iib = 
            InstanceIdentifier.builder(Bgp.class)
                              .child(Vrfs.class, new VrfsKey(rd));
        InstanceIdentifier<Vrfs> iid = iib.build();
        delete(iid);
    }

    private static final ThreadFactory threadFactory = new ThreadFactoryBuilder()
        .setNameFormat("NV-BgpCfgMgr-%d").build();
    static ScheduledExecutorService executor = Executors.newScheduledThreadPool(1, threadFactory);
    /*
    * Remove Stale Marked Routes after timer expiry.
    */
    class RouteCleanup implements Callable<Integer> {

        public Integer call () {
            int totalCleared = 0;
            try {
            if (staledFibEntriesMap.isEmpty()) {
                LOG.info("BGP: RouteCleanup timertask tirggered but STALED FIB MAP is EMPTY");
            } else {
                for (String rd : staledFibEntriesMap.keySet()) {
                    if (Thread.interrupted()) {
                        return 0;
                    }
                    Map<String, String> map = staledFibEntriesMap.get(rd);
                    if (map != null) {
                        for (String prefix : map.keySet()) {
                            if (Thread.interrupted()) {
                                return 0;
                            }
                            try {
                                totalCleared++;
                                bgpManager.deletePrefix(rd, prefix);
                            } catch (Exception e) {
                                LOG.error("BGP: RouteCleanup deletePrefix failed rd:{}, prefix{}" + rd.toString() + prefix);
                            }
                        }
                    }
                }
            }
            } catch(Exception e) {
                LOG.error("Cleanup Thread Got interrupted, Failed to cleanup stale routes ", e);
            } finally {
                staledFibEntriesMap.clear();
            }
            LOG.error("cleared {} stale routes after bgp restart", totalCleared);
            return 0;
        }
    }

    /*
     * BGP restart scenario, ODL-BGP manager was/is running.
     * On re-sync notification, Get a copy of FIB database.
     */
    public static void createStaleFibMap() {
        int totalStaledCount = 0;
        try {
            staledFibEntriesMap.clear();
            InstanceIdentifier<FibEntries> id = InstanceIdentifier.create(FibEntries.class);
            DataBroker db = BgpUtil.getBroker();
            if (db == null) {
                LOG.error("Couldn't find BgpUtil broker while creating createStaleFibMap");
                return;
            }
    
            Optional<FibEntries> fibEntries = BgpUtil.read(BgpUtil.getBroker(),
                    LogicalDatastoreType.CONFIGURATION, id);
            if (fibEntries.isPresent()) {
                List<VrfTables> stale_vrfTables = fibEntries.get().getVrfTables();
                for (VrfTables vrfTable : stale_vrfTables) {
                    Map<String, String> stale_fib_ent_map = new HashMap<>();
                    for (VrfEntry vrfEntry : vrfTable.getVrfEntry()) {
                        if (Thread.interrupted()) {
                            break;
                        }
                        totalStaledCount++;
                        //Create MAP from stale_vrfTables.
                        stale_fib_ent_map.put(vrfEntry.getDestPrefix(), vrfEntry.getNextHopAddress() + "/" + vrfEntry.getLabel());
                    }
                staledFibEntriesMap.put(vrfTable.getRouteDistinguisher(), stale_fib_ent_map);
                }
            } else {
                    LOG.error("createStaleFibMap:: FIBentries.class is not present");
            }
        } catch (Exception e) {
            LOG.error("createStaleFibMap:: erorr ", e);
        }
        LOG.error("created {} staled entries ", totalStaledCount);
    }

    //map<rd, map<prefix/len, nexthop/label>>
    public static Map<String, Map<String, String>> getStaledFibEntriesMap() {
        return staledFibEntriesMap;
    }


}
