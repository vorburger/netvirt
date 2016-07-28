package org.opendaylight.netvirt.aclservice.tests.utils.dags;

import static org.mockito.Mockito.mock;
import static org.opendaylight.netvirt.aclservice.tests.utils.MockitoNotImplementedExceptionAnswer.EXCEPTION_ANSWER;

import dagger.Provides;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.opendaylight.controller.config.api.DependencyResolver;
import org.opendaylight.controller.config.spi.Module;
import org.opendaylight.controller.config.spi.ModuleFactory;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.netvirt.aclservice.tests.idea.Mikito;
import org.opendaylight.netvirt.aclservice.tests.utils.ObjectRegistry;
import org.opendaylight.netvirt.aclservice.tests.utils.ObjectRepositoryDependencyResolver;
import org.opendaylight.netvirt.aclservice.tests.utils.ObjectRepositoryProviderContext;
import org.opendaylight.netvirt.aclservice.tests.utils.ObjectRepositoryRpcProviderRegistry;
import org.opendaylight.netvirt.aclservice.tests.utils.TestBindingAwareBroker;
import org.osgi.framework.BundleContext;

@dagger.Module
public abstract class AbstractBindingAndConfigTestModule {

    @Provides
    @Singleton
    BindingAwareBroker bindingAwareBroker() {
        return Mikito.stub(TestBindingAwareBroker.class);
    }

    @Provides
    @Singleton
    RpcProviderRegistry rpcProviderRegistry(Provider<ObjectRegistry> objectRepositoryProvider) {
        ObjectRepositoryRpcProviderRegistry rpcProviderRegistry = Mikito
                .stub(ObjectRepositoryRpcProviderRegistry.class);
        rpcProviderRegistry.setObjectRegistryProvider(objectRepositoryProvider);
        return rpcProviderRegistry;
    }

    @Provides
    @Singleton
    DependencyResolver dependencyResolver(Provider<ObjectRegistry> objectRepositoryProvider) {
        ObjectRepositoryDependencyResolver dependencyResolver = Mikito.stub(ObjectRepositoryDependencyResolver.class);
        dependencyResolver.setObjectRegistryProvider(objectRepositoryProvider);
        return dependencyResolver;
    }

    @Provides
    @Singleton
    ProviderContext providerContext(Provider<ObjectRegistry> objectRepositoryProvider) {
        ObjectRepositoryProviderContext sessionProviderContext = Mikito.stub(ObjectRepositoryProviderContext.class);
        sessionProviderContext.setObjectRegistryProvider(objectRepositoryProvider);
        return sessionProviderContext;
    }

    @Provides
    @Singleton
    Module module(DependencyResolver dependencyResolver) {
        BundleContext bundleContext = mock(BundleContext.class, EXCEPTION_ANSWER);
        ModuleFactory moduleFactory = moduleFactory();
        return moduleFactory.createModule("TEST", dependencyResolver, bundleContext);
    }

    @Provides
    @Singleton
    AutoCloseable moduleInstance(Module module, ProviderContext providerContext) {
        AutoCloseable moduleInstance = module.getInstance();
        BindingAwareProvider serviceProviderAsBindingAwareProvider = (BindingAwareProvider) moduleInstance;
        serviceProviderAsBindingAwareProvider.onSessionInitiated(providerContext);
        return moduleInstance;
    }

    protected abstract ModuleFactory moduleFactory();
}
