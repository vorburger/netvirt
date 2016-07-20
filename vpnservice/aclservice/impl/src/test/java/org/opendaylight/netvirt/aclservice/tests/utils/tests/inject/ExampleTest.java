package org.opendaylight.netvirt.aclservice.tests.utils.tests.inject;

import static org.junit.Assert.assertEquals;

import dagger.Component;
import javax.inject.Inject;
import org.junit.Rule;
import org.junit.Test;
import org.opendaylight.netvirt.aclservice.tests.utils.inject.Injector;
import org.opendaylight.netvirt.aclservice.tests.utils.inject.junit.InjectorRule;

public class ExampleTest {

    @Component(modules = TestModule1.class)
    interface Configuration extends Injector<ExampleTest> {
        void inject(ExampleTest test);
    }

    @Rule
    public InjectorRule injector = new InjectorRule(DaggerExampleWithRuleTest_Configuration.create());

    @Inject
    Service service;

    @Test
    public void serviceTest() {
        assertEquals("hello, world", service.hi());
    }

}
