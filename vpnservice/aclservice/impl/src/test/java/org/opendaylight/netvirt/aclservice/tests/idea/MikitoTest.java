package org.opendaylight.netvirt.aclservice.tests.idea;

import static org.junit.Assert.*;
import java.io.File;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.Test;

public class MikitoTest {

    interface SomeService {

        void foo();

        String bar(String arg);

        // Most methods on real world services have complex input (and output objects), not just int or String
        int foobar(File f);
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

}
