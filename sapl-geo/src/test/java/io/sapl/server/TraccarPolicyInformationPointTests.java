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

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.interpreter.InitializationException;
import io.sapl.pdp.PolicyDecisionPointFactory;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import reactor.test.StepVerifier;

@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
public class TraccarPolicyInformationPointTests {

    String                            address;
    JsonNode                          authTemplate;
    private String                    path              = "src/test/resources/policies/%s";
    final static String               resourceDirectory = Paths.get("src", "test", "resources").toFile()
            .getAbsolutePath();
    @Container
    public static GenericContainer<?> traccarContainer  = new GenericContainer<>(
            DockerImageName.parse("traccar/traccar:latest")).withExposedPorts(8082)
            .withFileSystemBind(resourceDirectory + "/opt/traccar/logs", "/opt/traccar/logs", BindMode.READ_WRITE)
            .withFileSystemBind(resourceDirectory + "/opt/traccar/data", "/opt/traccar/data", BindMode.READ_WRITE)
            .withReuse(false);

//    @BeforeAll
//    void setUp() throws Exception {
//
//        address = traccarContainer.getHost() + ":" + traccarContainer.getMappedPort(8082);
//
//        var template = """
//                      {
//                "algorithm": "DENY_OVERRIDES",
//                "variables":
//                    {
//                        "TRACCAR_DEFAULT_CONFIG":
//                        {
//                            "user":"test@fake.de",
//                            "password":"1234",
//                            "server":"%s",
//                            "protocol": "http"
//                        }
//                    }
//                }
//                  """;
//
//        var json = String.format(template, traccarContainer.getHost() + ":" + traccarContainer.getMappedPort(8082));
//
//        var writer = new BufferedWriter(
//                new FileWriter(String.format(path, "/traccarPositionTestEnvironmentVariable/pdp.json")));
//        writer.write(json);
//        writer.close();
//        writer = new BufferedWriter(
//                new FileWriter(String.format(path, "/traccarGeofencesTestEnvironmentVariable/pdp.json")));
//        writer.write(json);
//        writer.close();
//
//    }
//
//    @Test
//    void PositionAuthenticateByEnvironmentVariable() throws JsonProcessingException, InitializationException {
//
//        var pdp = PolicyDecisionPointFactory.filesystemPolicyDecisionPoint(
//                String.format(path, "traccarPositionTestEnvironmentVariable"),
//                () -> List.of(new TraccarPolicyInformationPoint(new ObjectMapper())), List::of, List::of, List::of);
//
//        var authzSubscription = AuthorizationSubscription.of("subject", "action", "resource");
//        var pdpDecisionFlux   = pdp.decide(authzSubscription);
//
//        StepVerifier.create(pdpDecisionFlux)
//                .expectNextMatches(authzDecision -> authzDecision.getDecision() == Decision.PERMIT).thenCancel()
//                .verify();
//    }
//
//    @Test
//    void PositionAuthenticateByVariable() throws JsonProcessingException, InitializationException {
//
//        var pdp = PolicyDecisionPointFactory.filesystemPolicyDecisionPoint(String.format(path, "traccarPositionTest"),
//                () -> List.of(new TraccarPolicyInformationPoint(new ObjectMapper())), List::of, List::of, List::of);
//
//        var subject = new Subject("test@fake.de", "1234",
//                traccarContainer.getHost() + ":" + traccarContainer.getMappedPort(8082), 1);
//
//        var authzSubscription = AuthorizationSubscription.of(subject, "action", "resource");
//        var pdpDecisionFlux   = pdp.decide(authzSubscription);
//
//        StepVerifier.create(pdpDecisionFlux)
//                .expectNextMatches(authzDecision -> authzDecision.getDecision() == Decision.PERMIT).thenCancel()
//                .verify();
//    }
//
//    @Test
//    void GeofencesAuthenticateByEnvironmentVariable() throws JsonProcessingException, InitializationException {
//
//        var pdp = PolicyDecisionPointFactory.filesystemPolicyDecisionPoint(
//                String.format(path, "traccarGeofencesTestEnvironmentVariable"),
//                () -> List.of(new TraccarPolicyInformationPoint(new ObjectMapper())), List::of, List::of, List::of);
//
//        var authzSubscription = AuthorizationSubscription.of("subject", "action", "resource");
//        var pdpDecisionFlux   = pdp.decide(authzSubscription);
//
//        StepVerifier.create(pdpDecisionFlux)
//                .expectNextMatches(authzDecision -> authzDecision.getDecision() == Decision.PERMIT).thenCancel()
//                .verify();
//    }
//
//    @Test
//    void GeofencesAuthenticateByVariable() throws JsonProcessingException, InitializationException {
//
//        var pdp = PolicyDecisionPointFactory.filesystemPolicyDecisionPoint(String.format(path, "traccarGeofencesTest"),
//                () -> List.of(new TraccarPolicyInformationPoint(new ObjectMapper())), List::of, List::of, List::of);
//
//        var subject = new Subject("test@fake.de", "1234",
//                traccarContainer.getHost() + ":" + traccarContainer.getMappedPort(8082), 1);
//
//        var authzSubscription = AuthorizationSubscription.of(subject, "action", "resource");
//        var pdpDecisionFlux   = pdp.decide(authzSubscription);
//
//        StepVerifier.create(pdpDecisionFlux)
//                .expectNextMatches(authzDecision -> authzDecision.getDecision() == Decision.PERMIT).thenCancel()
//                .verify();
//    }
//
//    @Getter
//    @RequiredArgsConstructor
//    class Subject {
//        private final String user;
//        private final String password;
//        private final String server;
//        private final int    deviceId;
//
//    }

}
