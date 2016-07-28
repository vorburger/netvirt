package org.opendaylight.netvirt.aclservice.tests.utils;

import org.opendaylight.netvirt.aclservice.tests.utils.ObjectRegistry.SimpleObjectRegistry;

public class ObjectRegistryBuilder implements ObjectRegistry.Builder {

    final SimpleObjectRegistry buildingRegistry = new SimpleObjectRegistry();

    @Override
    public <T> void putInstance(T object, Class<T> lookupType) throws IllegalArgumentException {
        buildingRegistry.putInstance(object, lookupType);
    }

    @Override
    public ObjectRegistry build() {
        return new SimpleObjectRegistry(buildingRegistry);
    }

}
