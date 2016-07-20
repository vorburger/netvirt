package org.opendaylight.netvirt.aclservice.tests.utils.tests.inject;

import dagger.Module;
import dagger.Provides;

@Module
public class TestModule1 {

    static @Provides AnotherService provideAnotherDaggerService() {
        return new AnotherService() {

            @Override
            public String foo(String bar) {
                return "hello, " + bar;
            }
        };
    }

}
