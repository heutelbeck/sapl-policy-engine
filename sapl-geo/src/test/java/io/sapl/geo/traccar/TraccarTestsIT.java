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
package io.sapl.geo.traccar;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.interpreter.Val;
import io.sapl.geo.common.TraccarTestBase;
import reactor.test.StepVerifier;

@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
class TraccarTestsIT extends TraccarTestBase {

    private String authenticationTemplate = """
                {
                "user":"%s",
                "password":"%s",
            	"server":"%s",
            	"protocol":"http"
            	}
            """;
    private String authTemplate;

    @BeforeAll
    void setup() throws Exception {
        traccarContainer.start();

        email    = "test@fake.de";
        password = "1234";
        registerUser(email, password);
        final var sessionCookie = establishSession(email, password);
        deviceId = createDevice(sessionCookie);
        final var body  = """
                    {
                     "name":"fence1",
                     "description": "description1",
                     "area":"POLYGON ((29.031502836569203 33.089228694845474, 29.00276317262039 33.09128276117099, 29.016684945926443 33.13356229304324, 29.031502836569203 33.089228694845474))"
                    }
                """;
        final var body2 = """
                    {
                     "name":"fence2",
                     "description": "description2",
                     "area":"POLYGON ((29.042083035750323 32.98056450865346, 28.946694589751544 32.987488754266934, 28.95426831559641 33.09568009197196, 29.03678598719543 33.05673121039777, 29.042083035750323 32.98056450865346))"
                    }
                """;

        final var body3 = """
                    {
                     "name":"fence3",
                     "description": "description3",
                     "area":"POLYGON ((29.144943123387407 32.68974619290327, 28.998185000516372 32.7183087060568, 29.135115352003865 32.85246596481156, 29.144943123387407 32.68974619290327))"
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
        postTraccarGeofence(sessionCookie, body3).block();
        addTraccarPosition("1234567890", 29D, 33D).block();
        final var address = traccarContainer.getHost() + ":" + traccarContainer.getMappedPort(8082);
        authTemplate = String.format(authenticationTemplate, email, password, address);
    }

    @ParameterizedTest
    @Execution(ExecutionMode.CONCURRENT)
    @CsvSource({ "WKT,PositionWKT,true", "GEOJSON,PositionGeoJsonSwitchedCoordinates,false", "GML,PositionGML,true",
            "KML,PositionKML,true" })
    void testTraccarPositions(String responseFormat, String expectedJsonKey, boolean latitudeFirst) throws Exception {

        final var expected         = source.getJsonSource().get(expectedJsonKey).toPrettyString();
        final var str              = """
                {
                   "responseFormat":"%s",
                   "deviceId":"%s"
                   """;
        var       responseTemplate = String.format(str, responseFormat, deviceId);
        if (!latitudeFirst) {
            responseTemplate = responseTemplate.concat(",\"latitudeFirst\":false");
        }
        responseTemplate = responseTemplate.concat("}");
        final var val    = Val.ofJson(responseTemplate);
        final var result = new TraccarPositions(Val.ofJson(authTemplate).get(), new ObjectMapper())
                .getPositions(val.get()).map(Val::get).map(JsonNode::toPrettyString);

        StepVerifier.create(result).expectNext(expected).thenCancel().verify();
    }

    @ParameterizedTest
    @Execution(ExecutionMode.CONCURRENT)
    @CsvSource({ "WKT,TraccarGeofencesDeviceWKT,true", "GEOJSON,TraccarGeofencesDeviceGeoJsonSwitchedCoordinates,false",
            "GML,TraccarGeofencesDeviceGML,true", "KML,TraccarGeofencesDeviceKML,true" })
    void testTraccarGeofencesWithDeviceId(String responseFormat, String expectedJsonKey, boolean latitudeFirst)
            throws Exception {

        final var expected         = source.getJsonSource().get(expectedJsonKey).toPrettyString();
        final var str              = """
                {
                   "responseFormat":"%s",
                   "deviceId":"%s"
                   """;
        var       responseTemplate = String.format(str, responseFormat, deviceId);
        if (!latitudeFirst) {
            responseTemplate = responseTemplate.concat(",\"latitudeFirst\":false");
        }
        responseTemplate = responseTemplate.concat("}");
        final var val    = Val.ofJson(responseTemplate);
        final var result = new TraccarGeofences(Val.ofJson(authTemplate).get(), new ObjectMapper())
                .getGeofences(val.get()).map(Val::get).map(JsonNode::toPrettyString);

        StepVerifier.create(result).expectNext(expected).expectNext(expected).thenCancel().verify();
    }

    @ParameterizedTest
    @Execution(ExecutionMode.CONCURRENT)
    @CsvSource({ "WKT,TraccarGeofencesWKT,true", "GEOJSON,TraccarGeofencesGeoJsonSwitchedCoordinates,false",
            "GML,TraccarGeofencesGML,true", "KML,TraccarGeofencesKML,true" })
    void testTraccarGeofencesWithoutDeviceId(String responseFormat, String expectedJsonKey, boolean latitudeFirst)
            throws Exception {

        final var expected         = source.getJsonSource().get(expectedJsonKey).toPrettyString();
        final var str              = """
                {
                   "responseFormat":"%s"
                   """;
        var       responseTemplate = String.format(str, responseFormat);
        if (!latitudeFirst) {
            responseTemplate = responseTemplate.concat(",\"latitudeFirst\":false");

        }
        responseTemplate = responseTemplate.concat("}");
        final var val    = Val.ofJson(responseTemplate);
        final var result = new TraccarGeofences(Val.ofJson(authTemplate).get(), new ObjectMapper())
                .getGeofences(val.get()).map(Val::get).map(JsonNode::toPrettyString);

        StepVerifier.create(result).expectNext(expected).expectNext(expected).thenCancel().verify();
    }

    @Test
    void testTraccarGeofencesRepetitionsAndPollingInterval() throws Exception {

        final var expected         = source.getJsonSource().get("TraccarGeofencesWKT").toPrettyString();
        final var str              = """
                {
                   "responseFormat":"WKT"
                   """;
        final var responseTemplate = str.concat("""
                   ,"repetitions" : 3
                   ,"pollingIntervalMs" : 1000
                }
                """);
        final var val              = Val.ofJson(responseTemplate);
        final var result           = new TraccarGeofences(Val.ofJson(authTemplate).get(), new ObjectMapper())
                .getGeofences(val.get()).map(Val::get).map(JsonNode::toPrettyString);

        StepVerifier.create(result).expectNext(expected).expectNext(expected).expectNext(expected).expectComplete()
                .verify();
    }

}
