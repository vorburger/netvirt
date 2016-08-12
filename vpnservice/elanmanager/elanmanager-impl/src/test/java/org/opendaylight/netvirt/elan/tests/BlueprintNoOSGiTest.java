/*
 * Copyright (c) 2016 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.tests;

import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.apache.aries.blueprint.NamespaceHandler;
import org.junit.Test;
import org.opendaylight.controller.blueprint.ext.OpendaylightNamespaceHandler;

/**
 * TODO ...
 *
 * @author Michael Vorburger
 */
public class BlueprintNoOSGiTest {

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

        List<URL> pathList = getClasspathResourcesURLs(loader, blueprintXmlNames);
        HackedBlueprintContainerImpl container = new HackedBlueprintContainerImpl(loader, pathList, null, handlers, false);
        container.init(true);
        container.destroy();
    }

    private void addNamespaceHandler(SimpleNamespaceHandlerSet nsHandlerSet, String ns, NamespaceHandler nsHandler) {
        nsHandlerSet.addNamespace(URI.create(ns), nsHandler.getSchemaLocation(ns), nsHandler);
    }

    private List<URL> getClasspathResourcesURLs(ClassLoader loader, String... blueprintXmlNames) {
        List<URL> urls = new ArrayList<>(blueprintXmlNames.length);
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
