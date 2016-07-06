package org.opendaylight.netvirt.aclservice.tests;

import static org.mockito.Mockito.mock;

import java.lang.reflect.Modifier;
import org.apache.commons.lang3.NotImplementedException;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Mike's mocker.
 *
 * TODO Can Mockito actually also do this, and I'm just not getting it?
 *
 * @author Michael Vorburger
 */
public class Mikito {

    public static <T> T stub(Class<T> interfaceToStub) {
        T stub = mock(interfaceToStub, new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                if (Modifier.isAbstract(invocation.getMethod().getModifiers())) {
                    throw new NotImplementedException(invocation.getMethod().getName() + " is not stubbed");
                } else {
                    return invocation.callRealMethod();
                }
            }
        });
        return stub;
    }

}
