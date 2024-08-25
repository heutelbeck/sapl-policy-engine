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

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.geo.common.TestBase;
import io.sapl.interpreter.InitializationException;
import io.sapl.pdp.PolicyDecisionPointFactory;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import reactor.test.StepVerifier;

@TestInstance(Lifecycle.PER_CLASS)
@Testcontainers
public class OwnTracksPolicyInformationPointTestsIT extends TestBase {

    private String path = "src/test/resources/policies/%s";
    private String server;

    public static final GenericContainer<?> owntracksRecorder = new GenericContainer<>(
            DockerImageName.parse("owntracks/recorder:latest")).withExposedPorts(8083).withEnv("OTR_PORT", "0") // disable
                                                                                                                // mqtt
            .withReuse(false);

    @BeforeAll
    void setUp() throws IOException {
        owntracksRecorder.start();
        var webClient = WebClient.builder().build();
        server = String.format("%s:%s", owntracksRecorder.getHost(), owntracksRecorder.getMappedPort(8083));
        var urlString = String.format("http://%s/pub", server); // URL des OwnTracks Servers
        var json      = """
                	{
                	"_type": "location",
                	"tid": "TD",
                	"lat": 47,
                	"lon": 13,
                	"tst": %s,
                	"batt": 99,
                	"acc": 14,
                	"alt": 100,
                	"created_at":"2023-07-09T13:34:19.000+00:00",
                	"inregions":[]}
                """;

        var payload = String.format(json, Instant.now().getEpochSecond());

        webClient.post().uri(urlString).header("X-Limit-U", "user").header("X-Limit-D", "device").bodyValue(payload)
                .exchangeToMono(response -> {

                    return response.bodyToMono(String.class);

                }).block();

        var template = """
                      {
                "algorithm": "DENY_OVERRIDES",
                "variables":
                	{
                		"OWNTRACKS_DEFAULT_CONFIG":
                		{
                			"server":"%s",
                			"protocol": %s
                		}
                	}
                }
                  """;

        var pdp = String.format(template, server, "http");
        writePdp(pdp, String.format(path, "/owntracksTestEnvironmentVariable/pdp.json"));

    }

    @Test
    void AuthenticateByVariable() throws InitializationException {

        var pdp = PolicyDecisionPointFactory.filesystemPolicyDecisionPoint(String.format(path, "owntracksTest"),
                () -> List.of(new OwnTracksPolicyInformationPoint(new ObjectMapper())), List::of, List::of, List::of);

        var server = String.format("%s:%s", owntracksRecorder.getHost(), owntracksRecorder.getMappedPort(8083));

        var subject = new Subject("user", "device", server);

        AuthorizationSubscription authzSubscription = AuthorizationSubscription.of(subject, "action", "resource");
        var                       pdpDecisionFlux   = pdp.decide(authzSubscription);

        StepVerifier.create(pdpDecisionFlux)
                .expectNextMatches(authzDecision -> authzDecision.getDecision() == Decision.PERMIT).thenCancel()
                .verify();
    }

    @Test
    void AuthenticateByEnvironmentVariable() throws InitializationException {

        var pdp = PolicyDecisionPointFactory.filesystemPolicyDecisionPoint(String.format(path, "owntracksTest"),
                () -> List.of(new OwnTracksPolicyInformationPoint(new ObjectMapper())), List::of, List::of, List::of);

        var server = String.format("%s:%s", owntracksRecorder.getHost(), owntracksRecorder.getMappedPort(8083));

        var subject = new Subject("user", "device", server);

        AuthorizationSubscription authzSubscription = AuthorizationSubscription.of(subject, "action", "resource");
        var                       pdpDecisionFlux   = pdp.decide(authzSubscription);

        StepVerifier.create(pdpDecisionFlux)
                .expectNextMatches(authzDecision -> authzDecision.getDecision() == Decision.PERMIT).thenCancel()
                .verify();
    }

    @Getter
    @RequiredArgsConstructor
    static class Subject {
        private final String user;
        private final String deviceId;
        private final String server;
    }

}
