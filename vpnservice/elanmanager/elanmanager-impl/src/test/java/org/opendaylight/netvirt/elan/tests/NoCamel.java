package org.opendaylight.netvirt.elan.tests;

import org.apache.camel.test.blueprint.CamelBlueprintTestSupport;
import org.junit.Test;

public class NoCamel extends CamelBlueprintTestSupport {

    @Override
    protected String getBlueprintDescriptor() {
        return "org/opendaylight/blueprint/elanmanager.xml";
    }

    @Test
    public void empty() {
    }

}
