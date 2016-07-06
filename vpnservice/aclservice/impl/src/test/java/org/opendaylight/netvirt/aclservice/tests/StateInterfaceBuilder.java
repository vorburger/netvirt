package org.opendaylight.netvirt.aclservice.tests;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;

/**
 * Alias for InterfaceBuilder.
 *
 * The point of this class is to be able to use
 * org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceBuilder
 * and
 * org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder
 * in the same class without requiring super ugly long fully qualified class names everywhere.
 *
 * @author Michael Vorburger
 */
public class StateInterfaceBuilder extends InterfaceBuilder {

    // TODO actually this is stupid.. one cannot repeat all setters as covariants here - think again about this

    @Override
    public StateInterfaceBuilder setName(final java.lang.String value) {
        super.setName(value);
        return this;
    }

    @Override
    public StateInterfaceBuilder setPhysAddress(PhysAddress value) {
        super.setPhysAddress(value);
        return this;
    }
}
