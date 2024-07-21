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
package io.sapl.server;

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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.geo.common.PostgisTestBase;
import io.sapl.interpreter.InitializationException;
import io.sapl.pdp.PolicyDecisionPointFactory;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import reactor.test.StepVerifier;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.List;

@TestInstance(Lifecycle.PER_CLASS)
@Testcontainers
class PostGisPolicyInformationPointTests extends PostgisTestBase {

    private String path = "src/test/resources/policies/%s";

    @BeforeAll
    void setUp() throws Exception {

        commonSetUp();

        var template = """
                      {
                "algorithm": "DENY_OVERRIDES",
                "variables":
                	{
                		"POSTGIS_DEFAULT_CONFIG":
                		{
                			"user":"%s",
                			"password":"%s",
                			"server":"%s",
                			"port": %s,
                			"dataBase":"%s"
                		}
                	}
                }
                  """;
        var json     = String.format(template, postgisContainer.getUsername(), postgisContainer.getPassword(),
                postgisContainer.getHost(), postgisContainer.getMappedPort(5432), postgisContainer.getDatabaseName());

        var writer = new BufferedWriter(
                new FileWriter(String.format(path, "/postgisTestEnvironmentVariable/pdp.json")));
        writer.write(json);

        writer.close();

    }

    @Test
    void AuthenticateByEnvironmentVariable() throws JsonProcessingException, InitializationException {

        var pdp = PolicyDecisionPointFactory.filesystemPolicyDecisionPoint(
                String.format(path, "postgisTestEnvironmentVariable"),
                () -> List.of(new PostGisPolicyInformationPoint(new ObjectMapper())), List::of, List::of, List::of);

        var authzSubscription = AuthorizationSubscription.of("subject", "action", "resource");
        var pdpDecisionFlux   = pdp.decide(authzSubscription);

        StepVerifier.create(pdpDecisionFlux)
                .expectNextMatches(authzDecision -> authzDecision.getDecision() == Decision.PERMIT).thenCancel()
                .verify();
    }

    @Test
    void AuthenticateByVariable() throws JsonProcessingException, InitializationException {

        var pdp = PolicyDecisionPointFactory.filesystemPolicyDecisionPoint(String.format(path, "postgisTest"),
                () -> List.of(new PostGisPolicyInformationPoint(new ObjectMapper())), List::of, List::of, List::of);

        var subject = new Subject(postgisContainer.getUsername(), postgisContainer.getPassword(),
                postgisContainer.getHost(), postgisContainer.getMappedPort(5432), postgisContainer.getDatabaseName());

        AuthorizationSubscription authzSubscription = AuthorizationSubscription.of(subject, "action", "resource");
        var                       pdpDecisionFlux   = pdp.decide(authzSubscription);

        StepVerifier.create(pdpDecisionFlux)
                .expectNextMatches(authzDecision -> authzDecision.getDecision() == Decision.PERMIT).thenCancel()
                .verify();
    }

    @Getter
    @RequiredArgsConstructor
    class Subject {
        private final String user;
        private final String password;
        private final String server;
        private final int    port;
        private final String dataBase;
    }

}
