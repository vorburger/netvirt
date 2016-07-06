package org.opendaylight.netvirt.aclservice.tests.idea;

import static org.mockito.Mockito.mock;
import java.lang.reflect.Modifier;
import org.apache.commons.lang3.NotImplementedException;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Mike's mocker.
 *
 * <p>Mikitos are:<ul>
 * <li>fully type safe and refactoring resistant; whereas Mockito is not, e.g.
 * for return values with doReturn(...).when(), and uses runtime instead of
 * compile time error reporting for this.
 * <li>enforce ExceptionAnswer by default (possible with Mockito, but is easily
 * forgotten)
 * <li>avoid confusion re. the alternative doReturn(...).when() syntax required
 * with ExceptionAnswer instead of when(...).thenReturn()
 * </ul>
 *
 * @author Michael Vorburger
 */
public class Mikito {

    public static <T> T stub(Class<T> abstractClass) {
        T stub = mock(abstractClass, new Answer<Object>() {
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
