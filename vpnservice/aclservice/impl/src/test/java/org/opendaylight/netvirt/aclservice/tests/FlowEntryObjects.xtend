package org.opendaylight.netvirt.aclservice.tests

import org.opendaylight.genius.mdsalutil.FlowEntity

class FlowEntryObjects {

    // TODO DOCUMENT: Prefer methods over fields, because:
    // 1. you can parameterize them, if needed
    // 2. static fields created only once would hide equals() implementation problems; new instances better

    static def flow1() {
        new FlowEntity(123) => [
            tableId = 22
            flowId = "Egress_DHCP_Client_v4123_0D:AA:D8:42:30:F3__Permit_"
            flowName = "ACL"
            priority = 61010
            cookie = 110100480BI

        ]
    }

}
