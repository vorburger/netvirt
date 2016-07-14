/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests.utils.tests

import org.junit.Test
import org.opendaylight.genius.mdsalutil.ActionInfoBuilder
import org.opendaylight.genius.mdsalutil.ActionType
import org.opendaylight.genius.mdsalutil.FlowEntity
import org.opendaylight.genius.mdsalutil.InstructionInfo
import org.opendaylight.genius.mdsalutil.InstructionType
import org.opendaylight.genius.mdsalutil.MatchFieldType
import org.opendaylight.genius.mdsalutil.MatchInfoBuilder
import org.opendaylight.genius.mdsalutil.NxMatchFieldType
import org.opendaylight.genius.mdsalutil.NxMatchInfoBuilder
import org.opendaylight.netvirt.aclservice.tests.FlowEntryObjects
import org.opendaylight.netvirt.aclservice.tests.utils.XtendBeanGenerator

import static org.junit.Assert.assertEquals

/**
 * Tests XtendBeanGenerator through FlowEntryObjects.
 * Note that not every *Objects needs a test like this;
 * this one was just written because it was the very first one.
 * @author Michael Vorburger
 */
class XtendBeanGeneratorFlowEntryObjectsTest {

    @Test def void expressionString() {
        assertEquals(
        '''
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_DHCP_Client_v4123_0D:AA:D8:42:30:F3__Permit_"
                flowName = "ACL"
                instructionInfoList += #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        (new ActionInfoBuilder => [
                            actionType = ActionType.nx_conntrack
                            actionValues = #[
                                "1",
                                "0",
                                "0",
                                "255"
                            ]
                        ]).build()
                    ]),
                    new InstructionInfo(InstructionType.goto_table, #[
                        41L
                    ])
                ]
                matchInfoList += #[
                    (new MatchInfoBuilder => [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            2048L
                        ]
                    ]).build(),
                    (new MatchInfoBuilder => [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            17L
                        ]
                    ]).build(),
                    (new MatchInfoBuilder => [
                        matchField = MatchFieldType.udp_dst
                        matchValues = #[
                            68L
                        ]
                    ]).build(),
                    (new MatchInfoBuilder => [
                        matchField = MatchFieldType.udp_src
                        matchValues = #[
                            67L
                        ]
                    ]).build(),
                    (new MatchInfoBuilder => [
                        matchField = MatchFieldType.eth_src
                        stringMatchValues = #[
                            "0D:AA:D8:42:30:F3"
                        ]
                    ]).build(),
                    (new NxMatchInfoBuilder => [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]).build()
                ]
                priority = 61010
                tableId = 40 as short
            ]'''.toString, new XtendBeanGenerator().getExpression(FlowEntryObjects::flow1()))
    }

    @Test def void expression() {
        assertEquals(
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_DHCP_Client_v4123_0D:AA:D8:42:30:F3__Permit_"
                flowName = "ACL"
                instructionInfoList += #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        (new ActionInfoBuilder => [
                            actionType = ActionType.nx_conntrack
                            actionValues = #[
                                "1",
                                "0",
                                "0",
                                "255"
                            ]
                        ]).build()
                    ]),
                    new InstructionInfo(InstructionType.goto_table, #[
                        41L
                    ])
                ]
                matchInfoList = #[
                    (new MatchInfoBuilder => [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            2048L
                        ]
                    ]).build(),
                    (new MatchInfoBuilder => [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            17L
                        ]
                    ]).build(),
                    (new MatchInfoBuilder => [
                        matchField = MatchFieldType.udp_dst
                        matchValues = #[
                            68L
                        ]
                    ]).build(),
                    (new MatchInfoBuilder => [
                        matchField = MatchFieldType.udp_src
                        matchValues = #[
                            67L
                        ]
                    ]).build(),
                    (new MatchInfoBuilder => [
                        matchField = MatchFieldType.eth_src
                        stringMatchValues = #[
                            "0D:AA:D8:42:30:F3"
                        ]
                    ]).build(),
                    (new NxMatchInfoBuilder => [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]).build()
                ]
                priority = 61010
                tableId = 40 as short
            ]
        , FlowEntryObjects::flow1())
    }

}
