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
package io.sapl.geo.pip;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.sapl.geo.common.TraccarTestBase;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
class TraccarPolicyInformationPointIT extends TraccarTestBase {

    private Subject subject;

    @BeforeAll
    void setUp() throws Exception {
        email    = "test@fake.de";
        password = "1234";
        registerUser(email, password);
        final var sessionCookie = establishSession(email, password);
        deviceId = createDevice(sessionCookie);
        subject  = new Subject(email, password, traccarContainer.getHost() + ":" + traccarContainer.getMappedPort(8082),
                deviceId);
        final var body  = """
                {
                 "name":"fence1",
                 "description": "description for fence1",
                 "area":"POLYGON ((48.25767 11.54370, 48.25767 11.54422, 48.25747 11.54422, 48.25747 11.54370, 48.25767 11.54370))"
                }
                """;
        final var body2 = """
                {
                 "name":"lmu",
                 "description": "description for lmu",
                 "area":"POLYGON ((48.150402911178844 11.566792870984045, 48.1483205765966 11.56544925428264, 48.147576865197465 11.56800995875841, 48.14969540929175 11.56935357546081, 48.150402911178844 11.566792870984045))"
                }
                """;

        final var traccarGeofences = new String[] { body, body2 };
        for (final var fence : traccarGeofences) {
            final var fenceRes = postTraccarGeofence(sessionCookie, fence).blockOptional();
            if (fenceRes.isPresent()) {
                linkGeofenceToDevice(deviceId, fenceRes.get().get("id").asInt(), sessionCookie);
            } else {
                throw new RuntimeException("Response was null");
            }
        }
        addTraccarPosition("1234567890", 51.34533, 7.40575).block();
        final var template = """
                 {
                  "algorithm": "DENY_OVERRIDES",
                  "variables":
                      {
                          "TRACCAR_DEFAULT_CONFIG":
                          {
                              "user":"%s",
                                    "password":"%s",
                              "server":"%s:%s",
                              "protocol": "%s"
                          }
                      }
                  }
                """;
        final var pdpJson  = String.format(template, email, password, traccarContainer.getHost(),
                traccarContainer.getMappedPort(8082), "http");
        writePdpJson(pdpJson);
        copyToTemp("/policies/traccarPositionTest/traccarTest.sapl");
    }
//
//    @ParameterizedTest
//    @Execution(ExecutionMode.CONCURRENT)
//    @CsvSource({ "traccarPositionTestEnvironmentVariable", "traccarPositionTest",
//            "traccarGeofencesTestEnvironmentVariable", "traccarGeofencesTest" })
//    void traccarPipTest(String pdpPath) throws InitializationException {
//        final var pdp               = PolicyDecisionPointFactory.filesystemPolicyDecisionPoint(
//                tempDir.toAbsolutePath().toString(),
//                () -> List.of(new TraccarPolicyInformationPoint(new ObjectMapper())), List::of, List::of, List::of);
//        final var authzSubscription = AuthorizationSubscription.of(subject, "action", "resource");
//        final var pdpDecisionFlux   = pdp.decide(authzSubscription);
//
//        StepVerifier.create(pdpDecisionFlux)
//                .expectNextMatches(authzDecision -> authzDecision.getDecision() == Decision.PERMIT).thenCancel()
//                .verify();
//    }

    @Getter
    @RequiredArgsConstructor
    static class Subject {
        private final String user;
        private final String password;
        private final String server;
        private final String deviceId;

    }
}
