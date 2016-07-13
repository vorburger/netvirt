package org.opendaylight.netvirt.aclservice.tests

import java.math.BigInteger
import org.opendaylight.genius.mdsalutil.ActionInfo
import org.opendaylight.genius.mdsalutil.FlowEntity
import org.opendaylight.genius.mdsalutil.InstructionInfo
import org.opendaylight.genius.mdsalutil.InstructionType
import org.opendaylight.genius.mdsalutil.MatchInfo
import org.opendaylight.genius.mdsalutil.NxMatchInfo

import static org.opendaylight.genius.mdsalutil.ActionType.*
import static org.opendaylight.genius.mdsalutil.InstructionType.*
import static org.opendaylight.genius.mdsalutil.MatchFieldType.*
import static org.opendaylight.genius.mdsalutil.NxMatchFieldType.*

// TODO DOCUMENT: Prefer methods over fields, because:
// 1. you can parameterize them, if needed
// 2. static fields created only once would hide equals() implementation problems; new instances better
class FlowEntryObjects {

    static def flow1() {
        new FlowEntity(new BigInteger("123")) => [
            tableId = 40 as short // TODO remove as short and BigInteger when https://git.opendaylight.org/gerrit/#/c/41519/ is merged
            flowId = "Egress_DHCP_Client_v4123_0D:AA:D8:42:30:F3__Permit_"
            flowName = "ACL"
            priority = 61010
            cookie = 110100480BI
            matchInfoList = #[
                new MatchInfo(eth_type, #[2048]),
                new MatchInfo(ip_proto, #[17]),
                new MatchInfo(udp_dst, #[68]),
                new MatchInfo(udp_src, #[67]),
                new MatchInfo(eth_src, #["0D:AA:D8:42:30:F3"]),
                new NxMatchInfo(ct_state, #[33, 33])
            ]
            instructionInfoList = #[
                new InstructionInfo(apply_actions, #[
                    new ActionInfo(nx_conntrack, #["1", "0", "0", "255"], 2)
                ]),
                new InstructionInfo(InstructionType.goto_table, #[41])
            ]
            strictFlag = false
            sendFlowRemFlag = false
        ]
    }

}
