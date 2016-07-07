package org.opendaylight.netvirt.aclservice.tests.utils;

import java.util.ArrayList;
import java.util.List;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;

/**
 * IMdsalApiManager useful for tests.
 *
 * Read
 * http://googletesting.blogspot.ch/2013/07/testing-on-toilet-know-your-test-doubles.html
 * for more about why this is called a "Fake*" and not a "Mock*" or "Stub*".
 *
 * This class is abstract just to save reading lines and typing keystrokes to
 * manually implement a bunch of methods we're not interested in. It is intended
 * to be used with Mikito.
 *
 * @author Michael Vorburger
 */
public abstract class FakeIMdsalApiManager implements IMdsalApiManager {

    private List<FlowEntity> flows;

    public List<FlowEntity> getFlows() {
        if (flows == null) {
            flows = new ArrayList<>();
        }
        return flows;
    }

    @Override
    public void installFlow(FlowEntity flowEntity) {
        getFlows().add(flowEntity);
    }

}
