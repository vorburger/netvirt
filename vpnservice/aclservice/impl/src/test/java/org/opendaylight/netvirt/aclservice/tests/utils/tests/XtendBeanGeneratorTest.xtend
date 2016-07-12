package org.opendaylight.netvirt.aclservice.tests.utils.tests

import org.junit.Test
import org.eclipse.xtend.lib.annotations.Accessors

/**
 * Unit test for XtendBeanGenerator.
 *
 * @author Michael Vorburger
 */
class XtendBeanGeneratorTest {

    @Test def void simple() {
        val b = new Bean
        b.name = "hello, world"
    }

    @Accessors
    private static class Bean {
        String name
    }

}
