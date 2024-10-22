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
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
public class OwnTracksPolicyInformationPointTestsIT extends TestBase {

    private String server;
    // @formatter:off
    @SuppressWarnings("resource") // Common test pattern
    public static final GenericContainer<?> owntracksRecorder =
            new GenericContainer<>(DockerImageName.parse("owntracks/recorder:latest"))
                .withExposedPorts(8083)
                .withEnv("OTR_PORT", "0") // disable mqtt
                .withReuse(false);
    // @formatter:on

    @BeforeAll
    void setUp() throws IOException, URISyntaxException {
        owntracksRecorder.start();
        final var webClient = WebClient.builder().build();
        server = String.format("%s:%s", owntracksRecorder.getHost(), owntracksRecorder.getMappedPort(8083));
        final var urlString = String.format("http://%s/pub", server); // URL des OwnTracks Servers
        final var json      = """
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
        final var payload   = String.format(json, Instant.now().getEpochSecond());
        webClient.post().uri(urlString).header("X-Limit-U", "user").header("X-Limit-D", "device").bodyValue(payload)
                .exchangeToMono(response -> {
                    return response.bodyToMono(String.class);
                }).block();

        final var template = """
                   {
                    "algorithm": "DENY_OVERRIDES",
                    "variables":
                    	{
                    		"OWNTRACKS_DEFAULT_CONFIG":
                    		{
                    			"server":"%s",
                    			"protocol": "%s"
                    		}
                    	}
                    }
                """;
        final var pdpJson  = String.format(template, server, "http");
        writePdpJson(pdpJson);
        copyToTemp("/policies/owntracksTest/owntracksTest.sapl");
    }

    @ParameterizedTest
    @Execution(ExecutionMode.CONCURRENT)
    @CsvSource({ "owntracksTest", "owntracksTestEnvironmentVariable" })
    void OwnTracksPipTest(String pdpPath) throws InitializationException {
        final var pdp               = PolicyDecisionPointFactory.filesystemPolicyDecisionPoint(
                tempDir.toAbsolutePath().toString(),
                () -> List.of(new OwnTracksPolicyInformationPoint(new ObjectMapper())), List::of, List::of, List::of);
        final var subject           = new Subject("user", "device", server);
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
        private final String deviceId;
        private final String server;
    }
}
