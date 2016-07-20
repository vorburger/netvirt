package org.opendaylight.netvirt.aclservice.tests.utils.inject;

public interface Injector<T> {

    void inject(T instance);

}
