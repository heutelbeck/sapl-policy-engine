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
package io.sapl.functions.libraries;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import lombok.val;

/**
 * Guards that schema validation never fetches a remote {@code $ref} URL. A
 * schema document is a function argument that can be supplied from the
 * authorization subscription, so resolving an unknown {@code $ref} over the
 * network would be a server-side request forgery. An unresolved reference must
 * fail validation locally, never trigger a connection.
 */
class SchemaRemoteRefSecurityTests {

    @Test
    @Timeout(30)
    @DisplayName("a schema $ref to a remote URL is never fetched")
    void schemaRefToRemoteUrlIsNotFetched() throws Exception {
        val connected = new AtomicBoolean(false);
        try (val server = new ServerSocket(0)) {
            val port     = server.getLocalPort();
            val acceptor = new Thread(() -> {
                             try {
                                 val socket = server.accept();
                                 connected.set(true);
                                 socket.close();
                             } catch (Exception ignored) {
                                 // the test asserts on 'connected'. A failed accept is a non-connection
                             }
                         });
            acceptor.setDaemon(true);
            acceptor.start();

            val schema  = (ObjectValue) Value.ofJson("""
                    { "type": "object", "properties": { "x": { "$ref": "http://127.0.0.1:%d/schema" } } }
                    """.formatted(port));
            val subject = Value.ofJson("{ \"x\": \"hello\" }");

            val resultDefault  = SchemaValidationLibrary.validate(subject, schema);
            val resultExternal = SchemaValidationLibrary.validateWithExternalSchemas(subject, schema, Value.ofJson("""
                    [ { "$id": "https://unrelated.example/x", "type": "object" } ]
                    """));

            acceptor.join(3000);

            assertThat(connected.get()).as("validating a schema with a remote $ref must not open a network connection")
                    .isFalse();
            assertThat(resultDefault).isInstanceOf(ErrorValue.class);
            assertThat(resultExternal).isInstanceOf(ErrorValue.class);
        }
    }
}
