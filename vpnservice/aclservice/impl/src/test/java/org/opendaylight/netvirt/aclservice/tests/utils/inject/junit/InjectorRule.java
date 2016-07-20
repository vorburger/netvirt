package org.opendaylight.netvirt.aclservice.tests.utils.inject.junit;

import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.opendaylight.netvirt.aclservice.tests.utils.inject.Injector;

@SuppressWarnings("rawtypes")
public class InjectorRule implements MethodRule {

    private final Injector injector;

    public InjectorRule(Injector injector) {
        this.injector = injector;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Statement apply(Statement base, FrameworkMethod method, Object target) {
        injector.inject(target);
        return base;
    }

}
