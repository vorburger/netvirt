package org.opendaylight.netvirt.elan.tests;

import java.util.ArrayList;
import java.util.List;
import org.apache.aries.blueprint.di.Recipe;
import org.osgi.service.blueprint.container.ComponentDefinitionException;

public class TestRecipe implements Recipe {

    @Override
    public String getName() {
        return "TEST";
    }

    @Override
    public List<Recipe> getConstructorDependencies() {
        return new ArrayList<>();
    }

    @Override
    public List<Recipe> getDependencies() {
        return new ArrayList<>();
    }

    @Override
    public Object create() throws ComponentDefinitionException {
        throw new IllegalStateException();
    }

    @Override
    public void destroy(Object instance) {
    }

}
