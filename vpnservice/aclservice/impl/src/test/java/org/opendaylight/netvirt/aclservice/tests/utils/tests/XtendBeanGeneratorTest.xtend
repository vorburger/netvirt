package org.opendaylight.netvirt.aclservice.tests.utils.tests

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test
import org.eclipse.xtend.lib.annotations.Accessors
import org.opendaylight.netvirt.aclservice.tests.utils.XtendBeanGenerator
import java.math.BigInteger
import java.util.List

/**
 * Unit test for XtendBeanGenerator.
 *
 * @author Michael Vorburger
 */
class XtendBeanGeneratorTest {

    val g = new XtendBeanGenerator()

    @Test def void simplestNumberExpression() {
        assertThatEndsWith(g.getExpression(123), "123")
    }

    @Test def void complexBean() {
        val bean = new Bean() => [
            ALongObject = 123L
            AShort = 123 as short
            anInt = 123
            anInteger = 123
            bigInteger = 456bi
            innerBean = new Bean() => [
                name = "1beanz"
            ]
            name = "hello, world"
            beanz = #[
                new Bean() => [
                    name = "beanz1"
                ]
            ]
        ]

        assertEquals('''
            // Code auto. generated by Michael Vorburger's org.opendaylight.netvirt.aclservice.tests.utils.XtendBeanGenerator
            new Bean() => [
                ALong = 0L
                ALongObject = 123L
                AShort = 123 as short
                anInt = 123
                anInteger = 123
                beanz = #[
                    new Bean() => [
                        ALong = 0L
                        AShort = 0 as short
                        anInt = 0
                        name = "beanz1"
                    ]
                ]
                bigInteger = 456bi
                innerBean = new Bean() => [
                    ALong = 0L
                    AShort = 0 as short
                    anInt = 0
                    name = "1beanz"
                ]
                name = "hello, world"
            ]'''.toString, g.getExpression(bean))
    }

    def private void assertThatEndsWith(String string, String endsWith) {
        assertTrue("'''" + string + "''' expected to endWith '''" + endsWith + "'''", string.endsWith(endsWith));
    }

    @Accessors
    public static class Bean {
        String name
        int anInt
        Integer anInteger
        long aLong
        Long aLongObject
        Long nullLong

        @Accessors(PUBLIC_GETTER) /* but no setter */ String onlyGetterString = "onlyGetterNoSetterString"

        short aShort
        BigInteger bigInteger

        Bean innerBean
        @Accessors(PUBLIC_GETTER) List<Bean> beanz = newArrayList
    }
}
