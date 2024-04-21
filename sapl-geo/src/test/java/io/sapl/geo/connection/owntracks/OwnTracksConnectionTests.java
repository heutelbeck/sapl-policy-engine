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
package io.sapl.geo.connection.owntracks;

import static org.junit.Assert.assertEquals;

import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.ObjectMapper;

import common.SourceProvider;
import io.sapl.api.interpreter.Val;

@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
public class OwnTracksConnectionTests {
    String         address;
    Integer        port;
    SourceProvider source = SourceProvider.getInstance();

    String template = """
                {
                "user":"user",
            	"server":"%s",
            	"protocol":"http",
            	"deviceId":1
            """;

    final static String resourceDirectory = Paths.get("src", "test", "resources").toFile().getAbsolutePath();

    @Container

    public static GenericContainer<?> owntracksRecorder = new GenericContainer<>(
            DockerImageName.parse("owntracks/recorder:latest")).withExposedPorts(8083)
            .withFileSystemBind(resourceDirectory + "/owntracks/store", "/store", BindMode.READ_WRITE)
            .withEnv("OTR_PORT", "0").withReuse(false);

    @BeforeAll
    void setup() {

        address  = owntracksRecorder.getHost() + ":" + owntracksRecorder.getMappedPort(8083);
        template = String.format(template, address);
    }

    @Test
    void Test01WKT() throws Exception {

        String exp = source.getJsonSource().get("ResponseWKT").toPrettyString();

        var tmp = template.concat(",\"responseFormat\":\"WKT\"}");

        var val = Val.ofJson(tmp);
        var res = new OwnTracksConnection(new ObjectMapper()).connect(val.get()).blockFirst().get().toPrettyString();

        assertEquals(exp, res);

    }

    @Test
    void Test02GeoJson() throws Exception {

        String exp = source.getJsonSource().get("ResponseGeoJsonSwitchedCoordinates").toPrettyString();

        var tmp = template.concat(",\"responseFormat\":\"GEOJSON\",\"latitudeFirst\":false}");

        var val = Val.ofJson(tmp);
        var res = new OwnTracksConnection(new ObjectMapper()).connect(val.get()).blockFirst().get().toPrettyString();

        assertEquals(exp, res);
    }

    @Test
    void Test03GML() throws Exception {

        String exp = source.getJsonSource().get("ResponseGML").toPrettyString();

        var tmp = template.concat(",\"responseFormat\":\"GML\"}");

        var val = Val.ofJson(tmp);
        var res = new OwnTracksConnection(new ObjectMapper()).connect(val.get()).blockFirst().get().toPrettyString();

        assertEquals(exp, res);

    }

    @Test
    void Test04KML() throws Exception {

        String exp = source.getJsonSource().get("ResponseKML").toPrettyString();

        var tmp = template.concat(",\"responseFormat\":\"KML\"}");

        var val = Val.ofJson(tmp);
        var res = new OwnTracksConnection(new ObjectMapper()).connect(val.get()).blockFirst().get().toPrettyString();

        assertEquals(exp, res);

    }

}
