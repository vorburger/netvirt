package org.opendaylight.netvirt.aclservice.tests.utils;

import org.apache.commons.lang3.NotImplementedException;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class MockitoNotImplementedExceptionAnswer implements Answer<Void> {

    public static final Answer<Void> ExceptionAnswer = new MockitoNotImplementedExceptionAnswer();

    private MockitoNotImplementedExceptionAnswer() { }

    @Override
    public Void answer(InvocationOnMock invocation) throws Throwable {
        throw new NotImplementedException(invocation.getMethod().getName() + " is not stubbed");
    }

}
