package org.opendaylight.netvirt.aclservice.tests.utils.dags;

import static org.mockito.Mockito.mock;
import static org.opendaylight.netvirt.aclservice.tests.utils.MockitoNotImplementedExceptionAnswer.EXCEPTION_ANSWER;

import dagger.Provides;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.opendaylight.controller.config.api.DependencyResolver;
import org.opendaylight.controller.config.spi.Module;
import org.opendaylight.controller.config.spi.ModuleFactory;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.netvirt.aclservice.tests.idea.Mikito;
import org.opendaylight.netvirt.aclservice.tests.utils.ObjectRegistry;
import org.opendaylight.netvirt.aclservice.tests.utils.ObjectRegistryBuilder;
import org.opendaylight.netvirt.aclservice.tests.utils.ObjectRepositoryDependencyResolver;
import org.opendaylight.netvirt.aclservice.tests.utils.ObjectRepositoryProviderContext;
import org.opendaylight.netvirt.aclservice.tests.utils.ObjectRepositoryRpcProviderRegistry;
import org.opendaylight.netvirt.aclservice.tests.utils.TestBindingAwareBroker;
import org.osgi.framework.BundleContext;

@dagger.Module
public abstract class AbstractBindingAndConfigTestModule {

    @Provides
    @Singleton
    ObjectRegistry.Builder objectRegistryBuilder(DataBroker dataBroker) {
        ObjectRegistryBuilder builder = new ObjectRegistryBuilder();
        builder.putInstance(dataBroker, DataBroker.class);
        return builder;
    }

    @Provides
    @Singleton
    ObjectRegistry objectRegistry(ObjectRegistry.Builder registryBuilder) {
        return registryBuilder.build();
    }

    @Provides
    @Singleton
    BindingAwareBroker bindingAwareBroker(ObjectRegistry.Builder registryBuilder) {
        TestBindingAwareBroker bindingAwareBroker = Mikito.stub(TestBindingAwareBroker.class);
        registryBuilder.putInstance(bindingAwareBroker, BindingAwareBroker.class);
        return bindingAwareBroker;
    }

    @Provides
    @Singleton
    RpcProviderRegistry rpcProviderRegistry(Provider<ObjectRegistry> objectRepositoryProvider,
            ObjectRegistry.Builder registryBuilder) {
        ObjectRepositoryRpcProviderRegistry rpcProviderRegistry = Mikito
                .stub(ObjectRepositoryRpcProviderRegistry.class);
        rpcProviderRegistry.setObjectRegistryProvider(objectRepositoryProvider);
        registryBuilder.putInstance(rpcProviderRegistry, RpcProviderRegistry.class);
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

    // The registryBuilder & bindingAwareBroker ARE dependencies of the Module
    // (Instance), just not explicit, but dynamic. By listing them here anyway,
    // even though not used in the body, DI engine can figure out requirements
    // and correct ordering.

    @Provides
    @Singleton
    Module module(ModuleFactory moduleFactory, DependencyResolver dependencyResolver,
            BindingAwareBroker bindingAwareBroker) {
        BundleContext bundleContext = mock(BundleContext.class, EXCEPTION_ANSWER);
        return moduleFactory.createModule("TEST", dependencyResolver, bundleContext);
    }

    @Provides
    @Singleton
    AutoCloseable moduleInstance(Module module, ProviderContext providerContext,
            RpcProviderRegistry rpcProviderRegistry) {
        AutoCloseable moduleInstance = module.getInstance();
        BindingAwareProvider serviceProviderAsBindingAwareProvider = (BindingAwareProvider) moduleInstance;
        serviceProviderAsBindingAwareProvider.onSessionInitiated(providerContext);
        return moduleInstance;
    }

}
