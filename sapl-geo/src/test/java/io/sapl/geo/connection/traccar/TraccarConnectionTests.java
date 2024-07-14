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
package io.sapl.geo.connection.traccar;

import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Paths;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import common.SourceProvider;
import io.sapl.api.interpreter.Val;
import io.sapl.geo.traccar.TraccarGeofences;
import io.sapl.geo.traccar.TraccarPositions;
import reactor.test.StepVerifier;

@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
public class TraccarConnectionTests {
    String         address;
    Integer        port;
    SourceProvider source   = SourceProvider.getInstance();
    String         template = """
                {
                "user":"test@fake.de",
                "password":"1234",
            	"server":"%s",
            	"protocol":"http"
            """;

    final static String resourceDirectory = Paths.get("src", "test", "resources").toFile().getAbsolutePath();

    @Container

    public static GenericContainer<?> traccarServer = new GenericContainer<>(
            DockerImageName.parse("traccar/traccar:latest")).withExposedPorts(8082)
            .withFileSystemBind(resourceDirectory + "/opt/traccar/logs", "/opt/traccar/logs", BindMode.READ_WRITE)
            .withFileSystemBind(resourceDirectory + "/opt/traccar/data", "/opt/traccar/data", BindMode.READ_WRITE)
            .withReuse(false);

    @BeforeAll
    void setup() {

        address  = traccarServer.getHost() + ":" + traccarServer.getMappedPort(8082);
        template = String.format(template, address);

    }

    @ParameterizedTest
    @Execution(ExecutionMode.CONCURRENT)
    @CsvSource({ "WKT,PositionWKT,true", "GEOJSON,PositionGeoJsonSwitchedCoordinates,false", "GML,PositionGML,true",
            "KML,PositionKML,true" })
    void testTraccarPositions(String responseFormat, String expectedJsonKey, boolean latitudeFirst) throws Exception {
        var expected         = source.getJsonSource().get(expectedJsonKey).toPrettyString();
        var responseTemplate = String.format(template + ",\"responseFormat\":\"%s\"", responseFormat);
        responseTemplate = responseTemplate.concat(",\"deviceId\":1");
        if (!latitudeFirst) {
            responseTemplate = responseTemplate.concat(",\"latitudeFirst\":false");

        }
        responseTemplate = responseTemplate.concat("}");
        var val    = Val.ofJson(responseTemplate);
        var result = new TraccarPositions(new ObjectMapper()).getPositions(val.get()).map(Val::get)
                .map(JsonNode::toPrettyString);

        StepVerifier.create(result).expectNext(expected).thenCancel().verify();

    }

    @ParameterizedTest
    @Execution(ExecutionMode.CONCURRENT)
    @CsvSource({ "WKT,TraccarGeofencesDeviceWKT,true", "GEOJSON,TraccarGeofencesDeviceGeoJsonSwitchedCoordinates,false",
            "GML,TraccarGeofencesDeviceGML,true", "KML,TraccarGeofencesDeviceKML,true" })
    void testTraccarGeofencesWithDeviceId(String responseFormat, String expectedJsonKey, boolean latitudeFirst)
            throws Exception {
        var expected         = source.getJsonSource().get(expectedJsonKey).toPrettyString();
        var responseTemplate = String.format(template + ",\"responseFormat\":\"%s\"", responseFormat);
        responseTemplate = responseTemplate.concat(",\"deviceId\":1");
        if (!latitudeFirst) {
            responseTemplate = responseTemplate.concat(",\"latitudeFirst\":false");

        }
        responseTemplate = responseTemplate.concat("}");
        var val    = Val.ofJson(responseTemplate);
        var result = new TraccarGeofences(new ObjectMapper()).getGeofences(val.get()).map(Val::get)
                .map(JsonNode::toPrettyString);

        StepVerifier.create(result).expectNext(expected).expectNext(expected).thenCancel().verify();
    }

    @ParameterizedTest
    @Execution(ExecutionMode.CONCURRENT)
    @CsvSource({ "WKT,TraccarGeofencesWKT,true", "GEOJSON,TraccarGeofencesGeoJsonSwitchedCoordinates,false",
            "GML,TraccarGeofencesGML,true", "KML,TraccarGeofencesKML,true" })
    void testTraccarGeofencesWithoutDeviceId(String responseFormat, String expectedJsonKey, boolean latitudeFirst)
            throws Exception {
        var expected         = source.getJsonSource().get(expectedJsonKey).toPrettyString();
        var responseTemplate = String.format(template + ",\"responseFormat\":\"%s\"", responseFormat);

        if (!latitudeFirst) {
            responseTemplate = responseTemplate.concat(",\"latitudeFirst\":false");

        }
        responseTemplate = responseTemplate.concat("}");
        var val    = Val.ofJson(responseTemplate);
        var result = new TraccarGeofences(new ObjectMapper()).getGeofences(val.get()).map(Val::get)
                .map(JsonNode::toPrettyString);

        StepVerifier.create(result).expectNext(expected).expectNext(expected).thenCancel().verify();
    }

    @Test
    void testTraccarGeofencesRepetitionsAndPollingInterval() throws Exception {
        var expected         = source.getJsonSource().get("TraccarGeofencesWKT").toPrettyString();
        var responseTemplate = String.format(template + ",\"responseFormat\":\"%s\"", "WKT");

        responseTemplate = responseTemplate.concat("""

                   ,"repetitions" : 3
                   ,"pollingIntervalMs" : 1000
                }
                """);
        var val    = Val.ofJson(responseTemplate);
        var result = new TraccarGeofences(new ObjectMapper()).getGeofences(val.get()).map(Val::get)
                .map(JsonNode::toPrettyString);

        StepVerifier.create(result).expectNext(expected).expectNext(expected).expectNext(expected).expectComplete()
                .verify();
    }

}
