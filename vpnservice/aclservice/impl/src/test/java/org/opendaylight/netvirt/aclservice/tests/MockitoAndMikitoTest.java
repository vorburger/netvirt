package org.opendaylight.netvirt.aclservice.tests;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import java.io.File;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.Test;
import org.mockito.stubbing.Answer;

public class MockitoAndMikitoTest {

    interface SomeService {

        void foo();

        String bar(String arg);

        // Most methods on real world services have complex input (and output objects), not just int or String
        int foobar(File f);
    }

    @Test
    public void usingMockitoToStubSimpleCase() {
        SomeService s = mock(SomeService.class);
        when(s.foobar(any())).thenReturn(123);
        assertEquals(123, s.foobar(new File("hello.txt")));
    }

    @Test
    public void usingMockitoToStubComplexCase() {
        SomeService s = mock(SomeService.class);
        when(s.foobar(any())).thenAnswer(invocation -> {
            // Urgh! This is ugly. Mockito may be better, see http://site.mockito.org/mockito/docs/current/org/mockito/ArgumentMatcher.html
            File f = (File) invocation.getArguments()[0];
            if (f.getName().equals("hello.txt")) {
                return 123;
            } else {
                return 0;
            }
        });
        assertEquals(0, s.foobar(new File("belo.txt")));
    }

    @Test(expected=NotImplementedException.class)
    public void usingMockitoExceptionException() {
        SomeService s = mock(SomeService.class, exceptionAnswer);
        s.foo();
    }

    @Test
    public void usingMockitoNoExceptionIfStubbed() {
        SomeService s = mock(SomeService.class, exceptionAnswer);
        // NOT when(s.foobar(any())).thenReturn(123) BUT must be like this:
        doReturn(123).when(s).foobar(any());
        assertEquals(123, s.foobar(new File("hello.txt")));
        try {
            s.foo();
            fail("expected NotImplementedException");
        } catch (NotImplementedException e) {
            // OK
        }
    }

    @Test
    public void usingMockitoToStubComplexCaseAndExceptionIfNotStubbed() {
        SomeService s = mock(SomeService.class, exceptionAnswer);
        doAnswer(invocation -> {
            // Urgh! This is ugly. Mockito may be better, see http://site.mockito.org/mockito/docs/current/org/mockito/ArgumentMatcher.html
            File f = (File) invocation.getArguments()[0];
            if (f.getName().equals("hello.txt")) {
                return 123;
            } else {
                return 0;
            }
        }).when(s).foobar(any());
        assertEquals(123, s.foobar(new File("hello.txt")));
        assertEquals(0, s.foobar(new File("belo.txt")));
    }

    @Test
    public void usingMikitoToCallStubbedMethod() {
        MockSomeService s = Mikito.stub(MockSomeService.class);
        assertEquals(123, s.foobar(new File("hello.txt")));
        assertEquals(0, s.foobar(new File("belo.txt")));
    }

    @Test(expected=NotImplementedException.class)
    public void usingMikitoToCallUnstubbedMethodAndExpectException() {
        MockSomeService s = Mikito.stub(MockSomeService.class);
        s.foo();
    }

    static abstract class MockSomeService implements SomeService {
        @Override
        public int foobar(File f) {
            if (f.getName().equals("hello.txt")) {
                return 123;
            } else {
                return 0;
            }
        }
    }

    static final Answer<Void> exceptionAnswer = invocation -> {
        throw new NotImplementedException(invocation.getMethod().getName() + " is not stubbed");
    };

}
