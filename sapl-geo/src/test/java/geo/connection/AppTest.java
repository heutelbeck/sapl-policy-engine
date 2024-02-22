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
package geo.connection;

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
import io.sapl.geo.connection.traccar.TraccarSocketManager;
import io.sapl.geo.pip.GeoPipResponseFormat;

@TestInstance(Lifecycle.PER_CLASS)
@Testcontainers
public class AppTest {
    String              address;
    Integer             port;
    SourceProvider      source            = SourceProvider.getInstance();
    final static String resourceDirectory = Paths.get("src", "test", "resources").toFile().getAbsolutePath();

    @Container
//	public static DockerComposeContainer traccarServer
//		= new DockerComposeContainer(new File(resourceDirectory + "/docker-compose.yml"))
//			.withExposedService("traccar", 8082)
//			;

    public static GenericContainer traccarServer = new GenericContainer(DockerImageName.parse("traccar/traccar:latest"))
            .withExposedPorts(8082)
            // .withExposedPorts(5000-5150)

            .withFileSystemBind(resourceDirectory + "/opt/traccar/logs", "/opt/traccar/logs", BindMode.READ_WRITE)
            .withFileSystemBind(resourceDirectory + "/opt/traccar/data", "/opt/traccar/data", BindMode.READ_WRITE)
            .withReuse(true);

    @BeforeAll
    void setup() {

        address = traccarServer.getHost() + ":" + traccarServer.getMappedPort(8082);
//		final var host = traccarServer.getServiceHost("traccar", 8082);
//		final var port = traccarServer.getServicePort("traccar", 8082);

    }

    @Test
    void test() {
        String exp = source.getJsonSource().get("ResponseWKT").toPrettyString();
        String res;

        var traccarSocket = TraccarSocketManager.getNew("test@fake.de", "1234", address, "http", 1, new ObjectMapper());
        res = traccarSocket.connect(GeoPipResponseFormat.WKT).blockFirst().toPrettyString();

//         System.out.println("result: " + res);
//         System.out.println("after subsciption");
        assertEquals(exp, res);

    }

}
