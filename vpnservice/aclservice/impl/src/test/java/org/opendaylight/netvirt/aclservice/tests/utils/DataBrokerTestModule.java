package org.opendaylight.netvirt.aclservice.tests.utils;

import dagger.Module;
import dagger.Provides;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.test.AbstractDataBrokerTest;
import org.opendaylight.netvirt.aclservice.tests.utils.inject.DaggerModuleProvideException;

@Module
public class DataBrokerTestModule {

    // Suppress IllegalCatch because of AbstractDataBrokerTest (change later)
    @SuppressWarnings("checkstyle:IllegalCatch")
    static @Provides DataBroker dataBroker() throws DaggerModuleProvideException {
        try {
            // This is a little bit "upside down" - in the future,
            // we should probably put what is in AbstractDataBrokerTest
            // into this DataBrokerTestModule, and make AbstractDataBrokerTest
            // use it, instead of the way around it currently is (the opposite);
            // this is just for historical reasons... and works for now.
            AbstractDataBrokerTest dataBrokerTest = new AbstractDataBrokerTest();
            dataBrokerTest.setup();
            return dataBrokerTest.getDataBroker();
        } catch (Exception e) {
            throw new DaggerModuleProvideException(e);
        }
    }
}
