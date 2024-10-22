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

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.geo.common.PostgisTestBase;
import io.sapl.interpreter.InitializationException;
import io.sapl.pdp.PolicyDecisionPointFactory;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import reactor.test.StepVerifier;

@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
class PostGisPolicyInformationPointTestsIT extends PostgisTestBase {

    private String path = "src/test/resources/policies/%s";

    @BeforeAll
    void setUp() throws Exception {

        commonSetUp();
        final var template = """
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
                      			"dataBase":"%s",
                      			"dataBaseType" : "POSTGIS"
                      		}
                      	}
                      }
                """;
        final var json     = String.format(template, postgisContainer.getUsername(), postgisContainer.getPassword(),
                postgisContainer.getHost(), postgisContainer.getMappedPort(5432), postgisContainer.getDatabaseName());

        writePdp(json, String.format(path, "/postgisTestEnvironmentVariable/pdp.json"));
    }

    @ParameterizedTest
    @Execution(ExecutionMode.CONCURRENT)
    @CsvSource({ "postgisTest", "postgisTestEnvironmentVariable" })
    void PostGisPipTest(String pdpPath) throws InitializationException {

        final var pdp               = PolicyDecisionPointFactory.filesystemPolicyDecisionPoint(
                String.format(path, pdpPath), () -> List.of(new PostGisPolicyInformationPoint(new ObjectMapper())),
                List::of, List::of, List::of);
        final var subject           = new Subject(postgisContainer.getUsername(), postgisContainer.getPassword(),
                postgisContainer.getHost(), postgisContainer.getMappedPort(5432), postgisContainer.getDatabaseName());
        final var authzSubscription = AuthorizationSubscription.of(subject, "action", "resource");
        final var pdpDecisionFlux   = pdp.decide(authzSubscription);

        StepVerifier.create(pdpDecisionFlux)
                .expectNextMatches(authzDecision -> authzDecision.getDecision() == Decision.PERMIT).thenCancel()
                .verify();
    }

    @Getter
    @RequiredArgsConstructor
    static class Subject {
        private final String user;
        private final String password;
        private final String server;
        private final int    port;
        private final String dataBase;
    }
}
