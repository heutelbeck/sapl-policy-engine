/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.node.cli.benchmark;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.sun.net.httpserver.HttpServer;

import lombok.val;

@DisplayName("HttpLoadGenerator")
class HttpLoadGeneratorTests {

    @Test
    @Timeout(30)
    @DisplayName("counts non-2xx responses as failures so the run reports failure")
    void whenServerReturnsErrorStatusThenFailuresCounted() throws Exception {
        val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(403, -1);
            exchange.close();
        });
        server.start();
        try {
            val baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            val sw      = new StringWriter();
            val out     = new PrintWriter(sw);

            val outcome = HttpLoadGenerator.run(baseUrl, "{}".getBytes(UTF_8), 1, 0, 1, 0, false, out);
            out.flush();

            assertThat(outcome.result()).isNotNull();
            assertThat(outcome.failures()).isPositive();
            assertThat(sw.toString()).contains("failed");
        } finally {
            server.stop(0);
        }
    }
}
