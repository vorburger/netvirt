/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests.utils.tests

import java.util.List
import org.eclipse.xtend.lib.annotations.Accessors
import org.junit.Test
import org.opendaylight.genius.mdsalutil.ActionInfo
import org.opendaylight.genius.mdsalutil.ActionInfoBuilder
import org.opendaylight.netvirt.aclservice.tests.utils.XtendBeanGenerator
import org.opendaylight.netvirt.aclservice.tests.utils.tests.XtendBeanGeneratorTest.Bean
import org.opendaylight.netvirt.aclservice.tests.utils.tests.XtendBeanGeneratorTest.BeanWithMultiConstructor
import org.opendaylight.netvirt.aclservice.tests.utils.tests.XtendBeanGeneratorTest.BeanWithMultiConstructorBuilder

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue

/**
 * Unit test for basic XtendBeanGenerator.
 *
 * @author Michael Vorburger
 */
class XtendBeanGeneratorBaseTest {

    static private class TestableXtendBeanGenerator extends XtendBeanGenerator {
        // Make some protected methods public so that we can test them here
        override public getBuilderClass(Object bean) {
            super.getBuilderClass(bean)
        }
    }

    val g = new TestableXtendBeanGenerator()

    @Test def void simplestNumberExpression() {
        assertThatEndsWith(g.getExpression(123), "123")
    }

    @Test def void simpleCharacter() {
        assertThatEndsWith(g.getExpression(new Character("c")), "'c'")
    }

    @Test def void nullCharacter() {
        var Character nullCharacter
        assertThatEndsWith(g.getExpression(nullCharacter), "null")
    }

    @Test def void defaultCharacter() {
        var char defaultCharacter
        assertThatEndsWith(g.getExpression(defaultCharacter), "")
    }

    @Test def void emptyString() {
        assertThatEndsWith(g.getExpression(""), "")
    }

    @Test def void aNull() {
        assertThatEndsWith(g.getExpression(null), "null")
    }

    @Test def void emptyList() {
        assertThatEndsWith(g.getExpression(#[]), "#[\n]")
    }

    @Test def void list() {
        assertThatEndsWith(g.getExpression(#["hi", "ho"]), "#[\n    \"hi\",\n    \"ho\"\n]")
    }

    @Test def void findEnclosingBuilderClass() {
        assertEquals(BeanWithBuilderBuilder,
            g.getBuilderClass(new BeanWithBuilderBuilder().build))
    }

    @Test def void findAdjacentBuilderClass() {
        assertEquals(BeanWithMultiConstructorBuilder,
            g.getBuilderClass(new BeanWithMultiConstructor(123)))
    }

    @Test def void findAdjacentBuilderClass2() {
        assertEquals(ActionInfoBuilder,
            g.getBuilderClass(new ActionInfo(null, null as String[])))
    }

    @Test def void emptyComplexBean() {
        assertEquals('''new Bean
            '''.toString, g.getExpression(new Bean))
    }

    @Test def void neverCallOnlyGettersIfThereIsNoSetter() {
        assertEquals("new ExplosiveBean\n", g.getExpression(new ExplosiveBean))
    }

    @Test def void testEnum() {
        assertEquals("TestEnum.a", g.getExpression(TestEnum.a))
    }

    @Test def void listBean() {
        val b = new ListBean => [
            strings += #[ "hi", "bhai" ]
        ]
        assertEquals(
            '''
            new ListBean => [
                strings += #[
                    "hi",
                    "bhai"
                ]
            ]'''.toString, g.getExpression(b))
    }

    def private void assertThatEndsWith(String string, String endsWith) {
        assertTrue("'''" + string + "''' expected to endWith '''" + endsWith + "'''", string.endsWith(endsWith));
    }

    public static enum TestEnum { a, b, c }

    public static class ListBean {
        @Accessors(PUBLIC_GETTER) /* but no setter */
        List<String> strings = newArrayList
    }

    public static class ExplosiveBean {
        String onlyGetter
        def String getOnlyGetter() {
            throw new IllegalStateException
        }
    }

}
