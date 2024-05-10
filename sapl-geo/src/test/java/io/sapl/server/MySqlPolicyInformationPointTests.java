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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import common.MySqlTestBase;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
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
class MySqlPolicyInformationPointTests extends MySqlTestBase {

    private String path = "src/test/resources/policies/%s";

    @BeforeAll
    void setUp() throws Exception {

        commonSetUp();

        String template = """
                      {
                "algorithm": "DENY_OVERRIDES",
                "variables":
                	{
                		"MYSQL_DEFAULT_CONFIG":
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
        String json     = String.format(template, mySqlContainer.getUsername(), mySqlContainer.getPassword(),
                mySqlContainer.getHost(), mySqlContainer.getMappedPort(3306), mySqlContainer.getDatabaseName());

        BufferedWriter writer = new BufferedWriter(
                new FileWriter(String.format(path, "/mysqlTestEnvironmentVariable/pdp.json")));
        writer.write(json);

        writer.close();

    }

    @Test
    void AuthenticateByEnvironmentVariable() throws JsonProcessingException, InitializationException {

        var pdp = PolicyDecisionPointFactory.filesystemPolicyDecisionPoint(
                String.format(path, "mysqlTestEnvironmentVariable"),
                () -> List.of(new MySqlPolicyInformationPoint(new ObjectMapper())), List::of, List::of, List::of);

        AuthorizationSubscription authzSubscription = AuthorizationSubscription.of("subject", "action", "resource");
        var                       pdpDecisionFlux   = pdp.decide(authzSubscription);

        StepVerifier.create(pdpDecisionFlux)
                .expectNextMatches(authzDecision -> authzDecision.getDecision() == Decision.PERMIT).thenCancel()
                .verify();
    }

    @Test
    void AuthenticateByVariable() throws JsonProcessingException, InitializationException {

        var pdp = PolicyDecisionPointFactory.filesystemPolicyDecisionPoint(String.format(path, "mysqlTest"),
                () -> List.of(new MySqlPolicyInformationPoint(new ObjectMapper())), List::of, List::of, List::of);

        var subject = new Subject(mySqlContainer.getUsername(), mySqlContainer.getPassword(), mySqlContainer.getHost(),
                mySqlContainer.getMappedPort(3306), mySqlContainer.getDatabaseName());

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
