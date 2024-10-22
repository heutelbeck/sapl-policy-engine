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
package io.sapl.geo.owntracks;

import java.nio.file.Paths;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.interpreter.Val;
import io.sapl.geo.common.SourceProvider;
import reactor.test.StepVerifier;

@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
public class OwnTracksTestsIT {

    String         address;
    SourceProvider source       = new SourceProvider();
    String         authTemplate = """
                {
            	"server":"%s",
            	"protocol":"http"
            	}
            """;
    String         template     = """
                {
                "user":"user",
            	"deviceId":1
            """;

    static final String RESOURCE_DIRECTORY = Paths.get("src", "test", "resources").toFile().getAbsolutePath();

    @Container
    @SuppressWarnings("resource") // Common test pattern
    public static final GenericContainer<?> owntracksRecorder = new GenericContainer<>(
            DockerImageName.parse("owntracks/recorder:latest")).withExposedPorts(8083).withEnv("OTR_PORT", "0") // disable
                                                                                                                // mqtt
            .withReuse(false);

    @BeforeAll
    void setup() {
        address      = owntracksRecorder.getHost() + ":" + owntracksRecorder.getMappedPort(8083);
        authTemplate = String.format(authTemplate, address);
        final var webClient = WebClient.builder().build();
        var       urlString = String.format("http://%s:%s/pub", owntracksRecorder.getHost(),
                owntracksRecorder.getMappedPort(8083));
        var       payload   = """
                	{
                	"_type": "location",
                	"tid": "TD",
                	"lat": 29,
                	"lon": 33,
                	"tst": 1712477261,
                	"batt": 69,
                	"acc": 14,
                	"alt": 100,
                	"created_at":"2023-07-09T13:34:19.000+00:00",
                	"inregions":["home"]
                	}
                """;
        webClient.post().uri(urlString).header("X-Limit-U", "user").header("X-Limit-D", "1").bodyValue(payload)
                .exchangeToMono(response -> {
                    return response.bodyToMono(String.class);
                }).block();
    }

    @ParameterizedTest
    @Execution(ExecutionMode.CONCURRENT)
    @CsvSource({ "WKT,ResponseWKT,true", "GEOJSON,ResponseGeoJsonSwitchedCoordinates,false", "GML,ResponseGML,true",
            "KML,ResponseKML,true" })
    void testGetPositionWithInregions(String responseFormat, String expectedJsonKey, boolean latitudeFirst)
            throws Exception {
        final var expected        = source.getJsonSource().get(expectedJsonKey).toPrettyString();
        var       requestTemplate = (template.concat(",\"responseFormat\":\"%s\""));
        requestTemplate = String.format(requestTemplate, responseFormat);

        if (!latitudeFirst) {
            requestTemplate = requestTemplate.concat(",\"latitudeFirst\":false");

        }
        requestTemplate = requestTemplate.concat("}");
        final var val          = Val.ofJson(requestTemplate);
        final var resultStream = new OwnTracks(Val.ofJson(authTemplate).get(), new ObjectMapper())
                .getPositionWithInregions(val.get()).map(Val::get).map(JsonNode::toPrettyString);
        StepVerifier.create(resultStream).expectNext(expected).thenCancel().verify();
    }

    @Test
    void testHttpAuth() throws JsonProcessingException {
        var authTemp = """
                    {
                    "server":"%s",
                    "protocol":"http",
                    "httpUser":"test",
                    "password":"test"
                    }
                """;
        authTemp = String.format(authTemp, address);
        final var expected        = source.getJsonSource().get("ResponseWKT").toPrettyString();
        var       requestTemplate = (template.concat(",\"responseFormat\":\"%s\""));
        requestTemplate = String.format(requestTemplate, "WKT");

        requestTemplate = requestTemplate.concat("}");
        final var val          = Val.ofJson(requestTemplate);
        final var resultStream = new OwnTracks(Val.ofJson(authTemp).get(), new ObjectMapper())
                .getPositionWithInregions(val.get()).map(Val::get).map(JsonNode::toPrettyString);
        StepVerifier.create(resultStream).expectNext(expected).thenCancel().verify();
    }

    @Test
    void testgetPositionWithInregionsRepetitionsAndPollingInterval() throws Exception {
        final var expected         = source.getJsonSource().get("ResponseWKT").toPrettyString();
        final var requestTemplate  = (template.concat(",\"responseFormat\":\"WKT\""));
        final var responseTemplate = requestTemplate.concat("""
                   ,"repetitions" : 3
                   ,"pollingIntervalMs" : 1000
                }
                """);
        final var val              = Val.ofJson(responseTemplate);
        final var result           = new OwnTracks(Val.ofJson(authTemplate).get(), new ObjectMapper())
                .getPositionWithInregions(val.get()).map(Val::get).map(JsonNode::toPrettyString);

        StepVerifier.create(result).expectNext(expected).expectNext(expected).expectNext(expected).expectComplete()
                .verify();
    }
}
