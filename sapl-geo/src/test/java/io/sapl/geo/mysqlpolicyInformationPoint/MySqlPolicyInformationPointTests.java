/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.geo.mysqlpolicyInformationPoint;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import common.MySqlTestBase;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.geo.connection.mysql.MySqlConnection;
import io.sapl.pdp.EmbeddedPolicyDecisionPoint;
import io.sapl.pdp.PolicyDecisionPointFactory;
import io.sapl.server.MySqlPolicyInformationPoint;
import reactor.test.StepVerifier;
import java.util.List;

@TestInstance(Lifecycle.PER_CLASS)
@Testcontainers
class MySqlPolicyInformationPointTests extends MySqlTestBase {

    private EmbeddedPolicyDecisionPoint pdp;

    @BeforeAll
    void setUp() throws Exception {

        commonSetUp();

        pdp = PolicyDecisionPointFactory.filesystemPolicyDecisionPoint("src/test/resources/policies",
                () -> List.of(new MySqlPolicyInformationPoint(new ObjectMapper())), List::of, List::of, List::of);

    }

    @Test
    void Test01MySqlConnection() throws JsonProcessingException {

//    	AuthorizationSubscription authzSubscription = AuthorizationSubscription.of("subject", "action", "resource");
//
//    	var pdpDecisionFlux = pdp.decide(authzSubscription);
//
//        StepVerifier.create(pdpDecisionFlux)
//                .expectNextMatches(authzDecision -> authzDecision.getDecision() == Decision.PERMIT).thenCancel()
//                .verify();
    }

}
