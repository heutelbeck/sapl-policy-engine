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
	String              address;
    Integer             port;
    SourceProvider      source            = SourceProvider.getInstance();
    final static String resourceDirectory = Paths.get("src", "test", "resources").toFile().getAbsolutePath();

    @Container

    public static GenericContainer<?> owntracksRecorder = new GenericContainer<>(
            DockerImageName.parse("owntracks/recorder:latest")).withExposedPorts(8083)
            .withFileSystemBind(resourceDirectory + "/owntracks/store", "/store", BindMode.READ_WRITE)
            .withEnv("OTR_PORT", "0")
            .withReuse(false);

    @BeforeAll
    void setup() {

        address = owntracksRecorder.getHost() + ":" + owntracksRecorder.getMappedPort(8083);
    }
    
    @Test
    void test() throws Exception {
        var exp = "{\"deviceId\":1,\"position\":{\"type\":\"Point\",\"coordinates\":[40,10],\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:4326\"}}},\"altitude\":409.0,\"lastUpdate\":\"1712477273\",\"accuracy\":20.0,\"geoFences\":[{\"name\":\"home\"},{\"name\":\"home2\"}]}";

        var st = """
                {
                "user":"user",
            	"server":"%s",
            	"protocol":"http",
            	"responseFormat":"GEOJSON",
            	"deviceId":1
            }
            """;
        
        var val = Val.ofJson(String.format(st, address));
        var res = OwnTracksConnection.connect(val.get(), new ObjectMapper()).blockFirst().get().toString();

        assertEquals(exp, res);

    }
    
}
