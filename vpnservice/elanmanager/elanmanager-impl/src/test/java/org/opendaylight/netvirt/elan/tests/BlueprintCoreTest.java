/*
 * Copyright (c) 2016 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.tests;

import static org.mockito.Mockito.mock;
import static org.opendaylight.yangtools.testutils.mockito.MoreAnswers.exception;

import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import org.apache.aries.blueprint.NamespaceHandler;
import org.apache.aries.blueprint.container.BlueprintContainerImpl;
import org.apache.aries.blueprint.container.NamespaceHandlerRegistry;
import org.apache.aries.blueprint.parser.NamespaceHandlerSet;
import org.apache.aries.proxy.ProxyManager;
import org.junit.Test;
import org.mockito.Mockito;
import org.opendaylight.controller.blueprint.ext.OpendaylightNamespaceHandler;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.blueprint.container.BlueprintListener;

/**
 * TODO ...
 *
 * @author Michael Vorburger
 */
public class BlueprintCoreTest {

    @Test
    public void testBlueprint() throws Exception {
        checkBlueprintXMLs(getClass().getClassLoader(), "elanmanager.xml", "commands.xml");
    }

    private void checkBlueprintXMLs(ClassLoader loader, String... blueprintXmlNames) throws Exception {
        SimpleNamespaceHandlerSet handlers = new SimpleNamespaceHandlerSet();
        addNamespaceHandler(handlers, OpendaylightNamespaceHandler.NAMESPACE_1_0_0, new OpendaylightNamespaceHandler());
        addNamespaceHandler(handlers, org.apache.karaf.shell.console.commands.NamespaceHandler.SHELL_NAMESPACE_1_0_0,
                new org.apache.karaf.shell.console.commands.NamespaceHandler());
        addNamespaceHandler(handlers, org.apache.karaf.shell.console.commands.NamespaceHandler.SHELL_NAMESPACE_1_1_0,
                new org.apache.karaf.shell.console.commands.NamespaceHandler());

        NamespaceHandlerRegistry handlerRegistry = new NamespaceHandlerRegistry() {
            @Override
            public NamespaceHandlerSet getNamespaceHandlers(Set<URI> uri, Bundle bundle) {
                return handlers;
            }

            @Override
            public void destroy() {
            }
        };

        Bundle bundle = mockBundle();
        BundleContext bundleContext = null;
        List<Object> pathList = getClasspathResourcesURLs(loader, blueprintXmlNames);
        Bundle extenderBundle = null;
        BlueprintListener eventDispatcher = null;
        ExecutorService executor = null;
        ScheduledExecutorService timer = null;
        ProxyManager proxyManager = null;
        BlueprintContainerImpl container = new BlueprintContainerImpl(bundle, bundleContext, extenderBundle,
                eventDispatcher, handlerRegistry, executor, timer, pathList, proxyManager);
        container.run();
        container.destroy();
    }

    private Bundle mockBundle(/*String symbolicName*/) {
        Bundle bundle = mock(Bundle.class, exception());
        Mockito.when(bundle.getSymbolicName()).thenReturn("test.test.test");
        return bundle;
    }

    private void addNamespaceHandler(SimpleNamespaceHandlerSet nsHandlerSet, String ns, NamespaceHandler nsHandler) {
        nsHandlerSet.addNamespace(URI.create(ns), nsHandler.getSchemaLocation(ns), nsHandler);
    }

    private List<Object> getClasspathResourcesURLs(ClassLoader loader, String... blueprintXmlNames) {
        List<Object> urls = new ArrayList<>(blueprintXmlNames.length);
        for (String blueprintXmlName : blueprintXmlNames) {
            String fqn = "org/opendaylight/blueprint/" + blueprintXmlName;
            URL url = loader.getResource(fqn);
            if (url == null) {
                throw new IllegalArgumentException("Resource not found on passed ClassLoader: " + fqn);
            }
            urls.add(url);
        }
        return urls;
    }

}
