package org.opendaylight.netvirt.aclservice.tests.utils.tests.inject;

import javax.inject.Inject;

public class Service {

    private final AnotherService anotherDaggerService;

    @Inject
    public Service(AnotherService another) {
        super();
        this.anotherDaggerService = another;
    }

    public String hi() {
        return anotherDaggerService.foo("world");
    }
}
