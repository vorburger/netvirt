package org.opendaylight.netvirt.aclservice.tests.utils

import java.util.Map
import org.mockito.cglib.core.ReflectUtils
import java.beans.PropertyDescriptor
import org.eclipse.xtext.xbase.lib.util.ReflectExtensions
import org.opendaylight.netvirt.aclservice.tests.FlowEntryObjects
import java.math.BigInteger
import java.util.List

class WithOperatorExpressionGenerator {

    // TODO Detect Constructor..
    // TODO Support Builder - automatically, just check CP for *Builder

    def printXtend(Object bean) {
        print(getXtendExpression(bean))
    }

    def getXtendExpression(Object bean) {
        '''
        new «bean.class.simpleName»() => [
           «FOR field : getBeanFields(bean).entrySet»
           «field.key» = «stringify(field.value)»
           «ENDFOR»
        ]'''
    }

    def stringify(Object object) {
        switch object {
//            Object[]  : '''#[ «FOR e : object»
//                «getXtendExpression(e)»
//            «ENDFOR»
//                           ]'''
            List<?>   : '''
                        #[
                            «FOR e : object»
                            «getXtendExpression(e)»
                            «ENDFOR»
                        ]'''
            String    : '''"«object»"'''
            Short     : '''«object» as short'''
            BigInteger: '''«object»bi'''
            default   : '''«object»'''
        }
    }

    def Map<String, Object> getBeanFields(Object bean) {
        // could also implement using:
        //   * org.eclipse.xtext.xbase.lib.util.ReflectExtensions.get(Object, String)
        //   * com.google.common.truth.ReflectionUtil.getField(Class<?>, String)
        //   * org.codehaus.plexus.util.ReflectionUtils
        val properties = ReflectUtils.getBeanGetters(bean.class)
        val map = newLinkedHashMap()
        for (property : properties) {
            if (property.writeMethod != null)
              map.put(property.name, property.readMethod.invoke(bean))
        }
        return map
    }

    def static void main(String[] args) {
        new WithOperatorExpressionGenerator().printXtend(FlowEntryObjects.flow1)
    }
}
