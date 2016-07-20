/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests.utils;

import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.MutableClassToInstanceMap;
import java.util.Optional;
import org.opendaylight.netvirt.aclservice.tests.idea.Mikito;

/**
 * Something to look up Object instances in.
 *
 * <p>Elsewhere also known as a Guice Injector, Context in the Spring Framework or JNDI, etc.
 *
 * @author Michael Vorburger
 */
public interface ObjectRegistry {

    // TODO (public static?) interface Reader { ... what's below
    // TODO (public static?) interface Writer { putInstance

    <T> Optional<T> getInstanceOptional(Class<T> expectedType);

    default <T> T getInstanceOrException(Class<T> expectedType) throws IllegalStateException {
        return this.getInstanceOptional(expectedType).orElseThrow(
            () -> new IllegalStateException("No object of this type registered: " + expectedType.getName()));
    }

    class SimpleObjectRegistry implements ObjectRegistry {

        private final ClassToInstanceMap<Object> map = MutableClassToInstanceMap.create();

        @SafeVarargs
        public final <T> void putInstance(T object, Class<T> lookupType, Class<T>... additionalLookupTypes)
                throws IllegalArgumentException {
            checkedPutInstance(lookupType, object);
            for (Class<T> additionalLookupType : additionalLookupTypes) {
                checkedPutInstance(additionalLookupType, object);
            }
        }

        private <T> void checkedPutInstance(Class<T> type, T object) throws IllegalArgumentException {
            if (map.containsKey(type)) {
                throw new IllegalArgumentException("Registry already has an Object for type " + type.getName() + ": "
                        + map.getInstance(type).toString());
            }
            map.putInstance(type, object);
        }

        @Override
        public <T> Optional<T> getInstanceOptional(Class<T> expectedType) {
            return (Optional<T>) Optional.ofNullable(map.getInstance(expectedType));
        }

    }

    class MikityObjectRegistry extends SimpleObjectRegistry {
        @Override
        public <T> Optional<T> getInstanceOptional(Class<T> expectedType) {
            return Optional.of(super.getInstanceOptional(expectedType).orElseGet(() -> Mikito.stub(expectedType)));
        }
    }
}
