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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import static org.junit.Assert.assertEquals;

import java.nio.file.Paths;
import org.junit.jupiter.api.BeforeAll;
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
public class TraccarConnectionTests {
    String              address;
    Integer             port;
    SourceProvider      source            = SourceProvider.getInstance();
    final static String resourceDirectory = Paths.get("src", "test", "resources").toFile().getAbsolutePath();

    @Container

    public static GenericContainer<?> traccarServer = new GenericContainer<>(
            DockerImageName.parse("traccar/traccar:latest")).withExposedPorts(8082)
            .withFileSystemBind(resourceDirectory + "/opt/traccar/logs", "/opt/traccar/logs", BindMode.READ_WRITE)
            .withFileSystemBind(resourceDirectory + "/opt/traccar/data", "/opt/traccar/data", BindMode.READ_WRITE)
            .withReuse(false);

    @BeforeAll
    void setup() {

        address = traccarServer.getHost() + ":" + traccarServer.getMappedPort(8082);
    }

    @Test
    void test() throws Exception {
        String exp = source.getJsonSource().get("ResponseWKT").toPrettyString();

        var st  = """
                    {
                    "user":"test@fake.de",
                    "password":"1234",
                	"server":"%s",
                	"protocol":"http",
                	"responseFormat":"WKT",
                	"deviceId":1
                }
                """;
        var val = Val.ofJson(String.format(st, address));
        var res = TraccarConnection.connect(val.get(), new ObjectMapper()).blockFirst().get().toPrettyString();

        assertEquals(exp, res);

    }

}
