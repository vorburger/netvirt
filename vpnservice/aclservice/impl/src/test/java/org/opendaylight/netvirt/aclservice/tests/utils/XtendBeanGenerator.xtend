package org.opendaylight.netvirt.aclservice.tests.utils

import java.beans.PropertyDescriptor
import java.lang.reflect.Constructor
import java.lang.reflect.Parameter
import java.math.BigInteger
import java.util.Arrays
import java.util.List
import java.util.Map
import java.util.Set
import org.eclipse.xtend.lib.annotations.Accessors
import org.eclipse.xtend.lib.annotations.FinalFieldsConstructor
import org.eclipse.xtext.xbase.lib.Functions.Function0
import org.eclipse.xtext.xbase.lib.util.ReflectExtensions
import org.mockito.cglib.core.ReflectUtils
import org.objenesis.Objenesis
import org.objenesis.ObjenesisStd
import org.objenesis.instantiator.ObjectInstantiator

/**
 * Magic. pure. Magic.
 *
 * Generates highly readable Java Bean object initialization code
 * based on the <a href="https://eclipse.org/xtend/documentation/203_xtend_expressions.html#with-operator">
 * Xtend With Operator</a>.  This syntax is very well suited e.g. to capture expected objects in test code.
 *
 * <p>Xtend is a cool JVM language which itself
 * transpiles to Java source code.  There are <a href="https://eclipse.org/xtend/download.html">plugins
 * for Eclipse and IntelliJ IDEA to work with Xtend</a> available.  It is also possible
 * to use Gradle's Continuous Build mode on the Command Line to get Xtend translated to Java on the fly.
 * (It would even be imaginable to use Xtend's runtime interpreter to allow reading *.xtend files and create
 * objects from them, similar to a JSON or XML unmarshalling library, without any code generation.)
 *
 * <p>PS: This implementation is currently written with performance characteristics intended for
 * manually dumping objects when writing tests.  In particular, no Java Reflection results are
 * cached so far. It is thus not suitable for serializing objects in production, yet.
 *
 * @author Michael Vorburger
 */
class XtendBeanGenerator {

    val Objenesis objenesis = new ObjenesisStd
    val ReflectExtensions xtendReflectExtensions = new ReflectExtensions

    def void print(Object bean) {
        System.out.println('''// Code auto. generated by Michael Vorburger's «class.name»''')
        System.out.println(getExpression(bean))
    }

    def String getExpression(Object bean) {
        stringify(bean).toString
    }

    def protected CharSequence getNewBeanExpression(Object bean) {
        val builderClass = getBuilderClass(bean)
        val isUsingBuilder = !builderClass.equals(bean.class)
        val properties = getBeanProperties(bean, builderClass).filter[name, property | !property.hasDefaultValue ]
        val constructorArguments = constructorArguments(bean, builderClass, properties)
        val filteredRemainingProperties = properties.filter[name, property |
            (property.isWriteable || property.isList)].values
        '''
        «IF isUsingBuilder»(«ENDIF»new «builderClass.simpleName»«constructorArguments»«IF !filteredRemainingProperties.empty» => [«ENDIF»
            «FOR property : filteredRemainingProperties»
            «property.name» «IF property.isList»+=«ELSE»=«ENDIF» «stringify(property.valueFunction.apply)»
            «ENDFOR»
        «IF !filteredRemainingProperties.empty»]«ENDIF»«IF isUsingBuilder»).build()«ENDIF»'''
    }

    def isList(Property property) {
        property.type.isAssignableFrom(List) // NOT || property.type.isArray
    }

    def protected Class<?> getBuilderClass(Object bean) {
        if (bean.class.enclosingClass?.simpleName?.endsWith("Builder"))
            bean.class.enclosingClass
        else {
            val classLoader = bean.class.classLoader
            val buildClassName = bean.class.name + "Builder"
            try {
                Class.forName(buildClassName, false, classLoader)
            } catch (ClassNotFoundException e) {
                bean.class
            }
        }
    }

    def protected constructorArguments(Object bean, Class<?> builderClass, Map<String, Property> properties) {
        val constructors = builderClass.constructors
        if (constructors.isEmpty) ''''''
        else {
            val constructor = findSuitableConstructor(constructors, properties.keySet)
            if (constructor == null) ''''''
            else {
                val parameters = constructor.parameters
                '''«FOR parameter : parameters BEFORE '(' SEPARATOR ', ' AFTER ')'»«getConstructorParameterValue(parameter, properties)»«ENDFOR»'''
            }
        }
    }

    def protected Constructor<?> findSuitableConstructor(Constructor<?>[] constructors, Set<String> propertyNames) {
        val possibleConstructors = newArrayList
        for (Constructor<?> constructor : constructors) {
            var suitableConstructor = true
            for (parameter : constructor.parameters) {
                if (!propertyNames.contains(getParameterName(parameter))) {
                    suitableConstructor = false
                }
            }
            if (suitableConstructor)
                possibleConstructors.add(constructor)
        }
        // Now filter it out to retain only those with the highest number of parameters
        val randomMaxParametersConstructor = possibleConstructors.maxBy[parameterCount]
        val retainedConstructors = possibleConstructors.filter[it.parameterCount == randomMaxParametersConstructor.parameterCount]
        if (retainedConstructors.size == 1)
            retainedConstructors.head
        else if (retainedConstructors.empty)
            throw new IllegalStateException("No suitable constructor found, write a *Builder to help, as none of these match: "
                + Arrays.toString(constructors) + "; for: " + propertyNames)
        else
            throw new IllegalStateException("More than 1 suitable constructor found; remove one or write a *Builder to help instead: "
                + retainedConstructors + "; for: " + propertyNames)
    }

    def protected getConstructorParameterValue(Parameter parameter, Map<String, Property> properties) {
        val constructorParameterName = getParameterName(parameter)
        val value = properties.get(constructorParameterName)
        if (value == null)
            throw new IllegalStateException(
                "Constructor parameter '" + constructorParameterName + "' not found in "
                + parameter.declaringExecutable + ", consider writing a *Builder; bean's properties: "
                + properties.keySet)
        properties.remove(constructorParameterName)
        return stringify(value.valueFunction.apply)
    }

    def getParameterName(Parameter parameter) {
        if (!parameter.isNamePresent)
            // https://docs.oracle.com/javase/tutorial/reflect/member/methodparameterreflection.html
            throw new IllegalStateException(
                "Needs javac -parameters; or, in Eclipse: 'Store information about method parameters (usable via "
                + "reflection)' in Window -> Preferences -> Java -> Compiler, for: " + parameter.declaringExecutable);
        parameter.name
    }

    def protected CharSequence stringify(Object object) {
        switch object {
            case null : "null"
            case object.class.isArray : stringifyArray(object)
            List<?>   : '''
                        #[
                            «FOR e : object SEPARATOR ','»
                            «stringify(e)»
                            «ENDFOR»
                        ]'''
            String    : '''"«object»"'''
            Integer   : '''«object»'''
            Long      : '''«object»L'''
            Boolean   : '''«object»'''
            Byte      : '''«object»'''
            Character : '''«"'"»«object»«"'"»'''
            Double    : '''«object»d'''
            Float     : '''«object»f'''
            Short     : '''«object» as short'''
            BigInteger: '''«object»bi'''
            Enum<?>   : '''«object.declaringClass.simpleName».«object.name»'''
            default   : '''«getNewBeanExpression(object)»'''
        }
    }

    def protected CharSequence stringifyArray(Object array) {
        switch array {
            byte[]    : '''
                        #[
                            «FOR e : array SEPARATOR ','»
                            «stringify(e)»
                            «ENDFOR»
                        ]'''
            boolean[] : '''
                        #[
                            «FOR e : array SEPARATOR ','»
                            «stringify(e)»
                            «ENDFOR»
                        ]'''
            char[] : '''
                        #[
                            «FOR e : array SEPARATOR ','»
                            «stringify(e)»
                            «ENDFOR»
                        ]'''
            double[] : '''
                        #[
                            «FOR e : array SEPARATOR ','»
                            «stringify(e)»
                            «ENDFOR»
                        ]'''
            float[] : '''
                        #[
                            «FOR e : array SEPARATOR ','»
                            «stringify(e)»
                            «ENDFOR»
                        ]'''
            int[]     : '''
                        #[
                            «FOR e : array SEPARATOR ','»
                            «stringify(e)»
                            «ENDFOR»
                        ]'''
            long[]    : '''
                        #[
                            «FOR e : array SEPARATOR ','»
                            «stringify(e)»
                            «ENDFOR»
                        ]'''
            short[]    : '''
                        #[
                            «FOR e : array SEPARATOR ','»
                            «stringify(e)»
                            «ENDFOR»
                        ]'''
            Object[]  : '''
                        #[
                            «FOR e : array SEPARATOR ','»
                            «stringify(e)»
                            «ENDFOR»
                        ]'''
        }
    }

    def protected Map<String, Property> getBeanProperties(Object bean, Class<?> builderClass) {
        // could also implement using:
        //   * org.eclipse.xtext.xbase.lib.util.ReflectExtensions.get(Object, String)
        //   * com.google.common.truth.ReflectionUtil.getField(Class<?>, String)
        //   * org.codehaus.plexus.util.ReflectionUtils
        val defaultValuesBean = newEmptyBeanForDefaultValues(builderClass)
        val propertyDescriptors = ReflectUtils.getBeanProperties(builderClass)
        val propertiesMap = newLinkedHashMap()
        for (propertyDescriptor : propertyDescriptors) {
            if (isPropertyConsidered(propertyDescriptor))
                propertiesMap.put(propertyDescriptor.name, new Property(
                    propertyDescriptor.name,
                    propertyDescriptor.writeMethod != null,
                    propertyDescriptor.propertyType,
                    [ | xtendReflectExtensions.invoke(bean, propertyDescriptor.readMethod.name)],
                    if (defaultValuesBean != null)
                        try {
                            xtendReflectExtensions.invoke(defaultValuesBean, propertyDescriptor.readMethod.name)
                        } catch (Throwable t) {
                            null
                        }
                    else
                        null
                ))
        }
        return propertiesMap
    }

    def boolean isPropertyConsidered(PropertyDescriptor propertyDescriptor) {
        true
    }

    def newEmptyBeanForDefaultValues(Class<?> builderClass) {
        try {
            builderClass.newInstance
        } catch (InstantiationException e) {
            // http://objenesis.org
            val ObjectInstantiator<?> builderClassInstantiator = objenesis.getInstantiatorOf(builderClass)
            builderClassInstantiator.newInstance
        }
    }

    @FinalFieldsConstructor @Accessors(PUBLIC_GETTER)
    protected static class Property {
        final String name
        final boolean isWriteable
        final Class<?> type
        final Function0<Object> valueFunction
        final Object defaultValue

        def boolean hasDefaultValue() {
            val value = try {
                valueFunction.apply
            } catch (Throwable t) {
                null
            }
            return if (value == null && defaultValue == null) {
                true
            } else if (value != null && defaultValue != null) {
                if (!type.isArray)
                    valueFunction.apply == defaultValue
                else switch defaultValue {
                    byte[]    : Arrays.equals(value as byte[],    defaultValue as byte[])
                    boolean[] : Arrays.equals(value as boolean[], defaultValue as boolean[])
                    char[]    : Arrays.equals(value as char[],    defaultValue as char[])
                    double[]  : Arrays.equals(value as double[],  defaultValue as double[])
                    float[]   : Arrays.equals(value as float[],   defaultValue as float[])
                    int[]     : Arrays.equals(value as int[],     defaultValue as int[])
                    long[]    : Arrays.equals(value as long[],    defaultValue as long[])
                    short[]   : Arrays.equals(value as short[],   defaultValue as short[])
                    Object[]  : Arrays.deepEquals(value as Object[], defaultValue as Object[])
                    default   : value.equals(defaultValue)
                }
            } else if (value == null || defaultValue == null) {
                false
            }
        }

    }
}
