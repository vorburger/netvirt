package org.opendaylight.netvirt.aclservice.tests.utils.tests

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;

import org.junit.Test
import org.eclipse.xtend.lib.annotations.Accessors
import org.opendaylight.netvirt.aclservice.tests.utils.XtendBeanGenerator
import java.math.BigInteger

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
        bean.ashort = Short.valueOf("123")
        bean.bigInteger = BigInteger.valueOf(456)

        assertThat(g.getExpression(bean), containsString('''
            new Bean() => [
               ashort = 123 as short
               bigInteger = 456bi
               name = "hello, world"
            ]'''))
    }


    @Accessors
    public static class Bean {
        String name
        @Accessors(PUBLIC_GETTER) /* but no setter */ String onlyGetterString = "onlyGetterNoSetterString"
        short ashort
        BigInteger bigInteger
    }

}
