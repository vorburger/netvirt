package org.opendaylight.netvirt.aclservice.tests.utils.tests

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;

import org.junit.Test
import org.eclipse.xtend.lib.annotations.Accessors
import org.opendaylight.netvirt.aclservice.tests.utils.XtendBeanGenerator

/**
 * Unit test for XtendBeanGenerator.
 *
 * @author Michael Vorburger
 */
class XtendBeanGeneratorTest {

    val g = new XtendBeanGenerator()

    @Test def void simple() {
        val bean = new Bean
        bean.name = "hello, world"
        assertThat(g.getExpression(bean), containsString('''
        new Bean() => [
           name = "hello, world"
        ]'''))
    }


    public static class Bean {
        @Accessors String name
        @Accessors(PUBLIC_GETTER) /* but no setter */ String onlyGetterString = "onlyGetterNoSetterString"
    }

}
