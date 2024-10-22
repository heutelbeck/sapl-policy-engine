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
package io.sapl.geo.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.geo.pip.GeoPipResponseFormat;

@TestInstance(Lifecycle.PER_CLASS)
class SharedTest {

    private ConnectionBaseTestClass        connectionBaseTestClass;
    private TrackerConnectionBaseTestClass trackerConnectionBaseTestClass;
    private ObjectMapper                   mapper;

    @BeforeAll
    void setup() {

        connectionBaseTestClass        = new ConnectionBaseTestClass();
        trackerConnectionBaseTestClass = new TrackerConnectionBaseTestClass();
        mapper                         = new ObjectMapper();
    }

    @Test
    void getUserTest() throws JsonProcessingException {

        final var requestSettings = """
                {
                            "user":"test"
                }
                """;
        final var missing         = """
                {
                            "noUser":"test"
                }
                """;

        final var request      = mapper.readTree(requestSettings);
        final var errorRequest = mapper.readTree(missing);
        final var response     = connectionBaseTestClass.getUser(request);

        assertEquals("test", response);
        assertThrows(PolicyEvaluationException.class, () -> connectionBaseTestClass.getUser(errorRequest));
    }

    @Test
    void getPasswordTest() throws JsonProcessingException {

        final var requestSettings = """
                {
                            "password":"test"
                }
                """;
        final var missing         = """
                {
                            "noPassword":"test"
                }
                """;

        final var request      = mapper.readTree(requestSettings);
        final var errorRequest = mapper.readTree(missing);
        final var response     = connectionBaseTestClass.getPassword(request);

        assertEquals("test", response);
        assertThrows(PolicyEvaluationException.class, () -> connectionBaseTestClass.getPassword(errorRequest));
    }

    @Test
    void getServerTest() throws JsonProcessingException {

        final var requestSettings = """
                {
                            "server":"test"
                }
                """;
        final var missing         = """
                {
                            "noServer":"test"
                }
                """;

        final var request      = mapper.readTree(requestSettings);
        final var errorRequest = mapper.readTree(missing);
        final var response     = connectionBaseTestClass.getServer(request);

        assertEquals("test", response);
        assertThrows(PolicyEvaluationException.class, () -> connectionBaseTestClass.getServer(errorRequest));
    }

    @Test
    void getResponseFormatTest() throws JsonProcessingException {

        final var requestSettings = """
                {
                            "responseFormat":"WKT"
                }
                """;
        final var deflt           = """
                {
                            "noResponseFormat":"test"
                }
                """;

        final var request         = mapper.readTree(requestSettings);
        final var defaultRequest  = mapper.readTree(deflt);
        final var response        = connectionBaseTestClass.getResponseFormat(request, mapper);
        final var defaultResponse = connectionBaseTestClass.getResponseFormat(defaultRequest, mapper);
        assertEquals(GeoPipResponseFormat.WKT, response);
        assertEquals(GeoPipResponseFormat.GEOJSON, defaultResponse);
    }

    @Test
    void getLatitudeFirstTest() throws JsonProcessingException {

        final var requestSettings = """
                {
                            "latitudeFirst":"false"
                }
                """;
        final var deflt           = """
                {
                            "noLatitudeFirst":"test"
                }
                """;

        final var request         = mapper.readTree(requestSettings);
        final var defaultRequest  = mapper.readTree(deflt);
        final var response        = connectionBaseTestClass.getLatitudeFirst(request);
        final var defaultResponse = connectionBaseTestClass.getLatitudeFirst(defaultRequest);
        assertFalse(response);
        assertTrue(defaultResponse);

    }

    @Test
    void getDeviceIdTest() throws JsonProcessingException {

        final var requestSettings = """
                {
                            "deviceId":"test"
                }
                """;
        final var missing         = """
                {
                            "noDeviceId":"test"
                }
                """;

        final var request      = mapper.readTree(requestSettings);
        final var errorRequest = mapper.readTree(missing);
        final var response     = trackerConnectionBaseTestClass.getDeviceId(request);
        assertEquals("test", response);
        assertThrows(PolicyEvaluationException.class, () -> trackerConnectionBaseTestClass.getDeviceId(errorRequest));
    }

    static class ConnectionBaseTestClass extends ConnectionBase {
    }

    static class TrackerConnectionBaseTestClass extends TrackerConnectionBase {
    }
}
