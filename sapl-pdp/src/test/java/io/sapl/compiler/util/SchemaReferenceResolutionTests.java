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
package io.sapl.compiler.util;

import io.sapl.api.model.*;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.ast.Literal;
import io.sapl.ast.SchemaStatement;
import io.sapl.ast.SubscriptionElement;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.functions.libraries.SchemaValidationLibrary;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static io.sapl.api.model.ValueJsonMarshaller.json;
import static io.sapl.compiler.policy.SchemaValidatorCompiler.compileValidator;
import static io.sapl.util.SaplTesting.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@Timeout(30)
@DisplayName("Schema reference resolution")
class SchemaReferenceResolutionTests {

    private static final String CLASSIFIED_CONTENT     = "classified-content";
    private static final String CLASSIFIED_SCHEMA      = """
            { "const": "classified-content" }
            """;
    private static final String CLASSPATH_RESOURCE     = "schema-resolution/classified-schema.json";
    private static final String FILE_PLACEHOLDER       = "{file}";
    private static final String PORT_PLACEHOLDER       = "{port}";
    private static final String REFUSAL_MESSAGE_PREFIX = "cannot be resolved";

    private static final String ADDRESS_SCHEMA = """
            {
                "$id": "https://schemas.example.com/address",
                "type": "object",
                "properties": { "city": { "type": "string" } },
                "required": [ "city" ]
            }
            """;

    /**
     * The two ways a policy author or a subscription can bring a JSON schema into
     * the PDP: a schema statement compiled with the PDP's {@code schemas} variable,
     * and the schema validation function library with its external schemas
     * argument. Both yield TRUE or FALSE for a decided validation and an
     * ErrorValue for a schema that cannot be used.
     */
    enum SchemaEntryPoint {
        SCHEMA_STATEMENT {
            @Override
            Value validate(Value subject, ObjectValue schema, ArrayValue schemas) {
                val statement = new SchemaStatement(SubscriptionElement.SUBJECT, true,
                        new Literal(schema, TEST_LOCATION), TEST_LOCATION);
                val variables = (ObjectValue) obj("schemas", schemas);
                try {
                    val validator = compileValidator(List.of(statement), compilationContext(variables));
                    val ctx       = evaluationContext(AuthorizationSubscription.of(subject, "read", "data"));
                    return validator instanceof PureOperator operator ? operator.evaluate(ctx) : (Value) validator;
                } catch (SaplCompilerException e) {
                    return Value.error(e.getMessage());
                }
            }
        },
        SCHEMA_FUNCTION {
            @Override
            Value validate(Value subject, ObjectValue schema, ArrayValue schemas) {
                return SchemaValidationLibrary.isCompliantWithExternalSchemas(subject, schema, schemas);
            }
        };

        abstract Value validate(Value subject, ObjectValue schema, ArrayValue schemas);
    }

    @TempDir
    Path tempDir;

    private String resolve(String referenceTemplate, ConnectionRecordingServer server) throws IOException {
        val classifiedFile = Files.writeString(tempDir.resolve("classified-schema.json"), CLASSIFIED_SCHEMA);
        return referenceTemplate.replace(PORT_PLACEHOLDER, String.valueOf(server.port())).replace(FILE_PLACEHOLDER,
                classifiedFile.toUri().toString());
    }

    private static ObjectValue schema(String jsonText) {
        return (ObjectValue) json(jsonText);
    }

    private static Stream<Arguments> foreignReferences() {
        val references = List.of(arguments("http", "http://127.0.0.1:{port}/schema"),
                arguments("https", "https://127.0.0.1:{port}/schema"),
                arguments("cloud metadata style http", "http://127.0.0.1:{port}/latest/meta-data/"),
                arguments("file", FILE_PLACEHOLDER), arguments("jar", "jar:" + FILE_PLACEHOLDER + "!/schema.json"),
                arguments("classpath", "classpath:" + CLASSPATH_RESOURCE),
                arguments("resource", "resource:" + CLASSPATH_RESOURCE),
                arguments("unknown scheme", "unknown-scheme://127.0.0.1:{port}/schema"),
                arguments("relative to the inline schema", "classified-schema.json"));
        return Stream.of(SchemaEntryPoint.values()).flatMap(
                entryPoint -> Stream.of("$ref", "$schema").flatMap(keyword -> references.stream().map(reference -> {
                    val values = reference.get();
                    return arguments(entryPoint, keyword, values[0], values[1]);
                })));
    }

    @Nested
    @DisplayName("Foreign references")
    class ForeignReferenceTests {

        @ParameterizedTest(name = "{0}: {1} to {2} is refused without any connection")
        @MethodSource("io.sapl.compiler.util.SchemaReferenceResolutionTests#foreignReferences")
        @DisplayName("a schema naming a resource outside the PDP fails closed and the PDP opens no connection")
        void whenSchemaReferencesForeignResourceThenRefusedWithoutConnection(SchemaEntryPoint entryPoint,
                String keyword, String kind, String referenceTemplate) throws IOException {
            try (val server = new ConnectionRecordingServer()) {
                val reference = resolve(referenceTemplate, server);
                val result    = entryPoint.validate(Value.of(CLASSIFIED_CONTENT), schema("""
                        { "%s": "%s" }
                        """.formatted(keyword, reference)), Value.EMPTY_ARRAY);

                assertThat(server.connections()).as("connections received by %s", reference).isZero();
                assertThat(result).isInstanceOfSatisfying(ErrorValue.class, error -> assertThat(error.message())
                        .contains(REFUSAL_MESSAGE_PREFIX).doesNotContain(CLASSIFIED_CONTENT));
            }
        }

        @Test
        @DisplayName("the probe server observes a connection, so a zero count proves the PDP stayed off the network")
        void whenClientConnectsToProbeServerThenConnectionIsRecorded() throws IOException {
            try (val server = new ConnectionRecordingServer();
                    val client = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
                client.getOutputStream().write("GET /schema HTTP/1.1\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                val statusLine = new BufferedReader(
                        new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8)).readLine();

                assertThat(statusLine).isEqualTo("HTTP/1.1 200 OK");
                assertThat(server.connections()).isOne();
            }
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(SchemaEntryPoint.class)
        @DisplayName("a schema from the schemas supplied to the PDP cannot pull in a foreign resource either")
        void whenSuppliedSchemaReferencesForeignResourceThenRefusedWithoutConnection(SchemaEntryPoint entryPoint)
                throws IOException {
            try (val server = new ConnectionRecordingServer()) {
                val supplied = schema("""
                        { "$id": "https://schemas.example.com/relay", "$ref": "http://127.0.0.1:%d/schema" }
                        """.formatted(server.port()));
                val result   = entryPoint.validate(Value.of(CLASSIFIED_CONTENT), schema("""
                        { "$ref": "https://schemas.example.com/relay" }
                        """), Value.ofArray(supplied));

                assertThat(server.connections()).isZero();
                assertThat(result).isInstanceOfSatisfying(ErrorValue.class,
                        error -> assertThat(error.message()).contains(REFUSAL_MESSAGE_PREFIX));
            }
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.sapl.compiler.util.SchemaReferenceResolutionTests#disclosingReferences")
        @DisplayName("the detailed validation result never discloses content of a referenced resource")
        void whenDetailedValidationReferencesForeignResourceThenNoContentDisclosed(String referenceTemplate)
                throws IOException {
            try (val server = new ConnectionRecordingServer()) {
                val reference = resolve(referenceTemplate, server);
                val result    = SchemaValidationLibrary.validate(Value.of("probe"), schema("""
                        { "$ref": "%s" }
                        """.formatted(reference)));

                assertThat(result).isInstanceOf(ErrorValue.class).asString().doesNotContain(CLASSIFIED_CONTENT);
            }
        }
    }

    private static Stream<String> disclosingReferences() {
        return Stream.of("http://127.0.0.1:{port}/schema", FILE_PLACEHOLDER, "classpath:" + CLASSPATH_RESOURCE,
                "resource:" + CLASSPATH_RESOURCE);
    }

    private static Stream<Arguments> builtInDrafts() {
        val drafts = List.of("http://json-schema.org/draft-04/schema#", "http://json-schema.org/draft-06/schema#",
                "http://json-schema.org/draft-07/schema#", "https://json-schema.org/draft/2019-09/schema",
                "https://json-schema.org/draft/2020-12/schema");
        return Stream.of(SchemaEntryPoint.values())
                .flatMap(entryPoint -> drafts.stream().map(draft -> arguments(entryPoint, draft)));
    }

    private static Stream<Arguments> builtInMetaSchemaReferences() {
        val metaSchemas = List.of("http://json-schema.org/draft-07/schema#",
                "https://json-schema.org/draft/2019-09/schema", "https://json-schema.org/draft/2020-12/schema",
                "https://json-schema.org/draft/2020-12/meta/validation");
        return Stream.of(SchemaEntryPoint.values())
                .flatMap(entryPoint -> metaSchemas.stream().map(metaSchema -> arguments(entryPoint, metaSchema)));
    }

    @Nested
    @DisplayName("Permitted references")
    class PermittedReferenceTests {

        @ParameterizedTest(name = "{0}: {1}")
        @MethodSource("io.sapl.compiler.util.SchemaReferenceResolutionTests#builtInDrafts")
        @DisplayName("every supported draft named by $schema keeps validating")
        void whenSchemaNamesSupportedDraftThenValidates(SchemaEntryPoint entryPoint, String draft) {
            val draftSchema = schema("""
                    { "$schema": "%s", "type": "string" }
                    """.formatted(draft));

            assertThat(List.of(entryPoint.validate(Value.of("text"), draftSchema, Value.EMPTY_ARRAY),
                    entryPoint.validate(Value.of(1), draftSchema, Value.EMPTY_ARRAY)))
                    .containsExactly(Value.TRUE, Value.FALSE);
        }

        @ParameterizedTest(name = "{0}: {1}")
        @MethodSource("io.sapl.compiler.util.SchemaReferenceResolutionTests#builtInMetaSchemaReferences")
        @DisplayName("a $ref to a built-in meta-schema resolves from the validator's bundled copy")
        void whenSchemaReferencesBuiltInMetaSchemaThenValidates(SchemaEntryPoint entryPoint, String metaSchema) {
            val metaSchemaReference = schema("""
                    { "$ref": "%s" }
                    """.formatted(metaSchema));

            assertThat(List.of(entryPoint.validate(schema("""
                    { "type": "string" }
                    """), metaSchemaReference, Value.EMPTY_ARRAY), entryPoint.validate(schema("""
                    { "minLength": "not a number" }
                    """), metaSchemaReference, Value.EMPTY_ARRAY))).containsExactly(Value.TRUE, Value.FALSE);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(SchemaEntryPoint.class)
        @DisplayName("a $ref to a schema supplied to the PDP by its $id keeps validating")
        void whenSchemaReferencesSuppliedSchemaThenValidates(SchemaEntryPoint entryPoint) {
            val supplied = Value.ofArray(schema(ADDRESS_SCHEMA));
            val order    = schema(
                    """
                            { "type": "object", "properties": { "shipping": { "$ref": "https://schemas.example.com/address" } } }
                            """);

            assertThat(List.of(entryPoint.validate(json("""
                    { "shipping": { "city": "Berlin" } }
                    """), order, supplied), entryPoint.validate(json("""
                    { "shipping": { "zip": "10115" } }
                    """), order, supplied))).containsExactly(Value.TRUE, Value.FALSE);
        }
    }

    /**
     * Accepts TCP connections on the loopback interface, counts them, and answers
     * every request with a schema whose content must never surface anywhere.
     */
    private static final class ConnectionRecordingServer implements AutoCloseable {

        private static final String RESPONSE = """
                HTTP/1.1 200 OK\r
                Content-Type: application/json\r
                Content-Length: %d\r
                Connection: close\r
                \r
                %s""".formatted(CLASSIFIED_SCHEMA.getBytes(StandardCharsets.UTF_8).length, CLASSIFIED_SCHEMA);

        private final AtomicInteger connections = new AtomicInteger();
        private final ServerSocket  serverSocket;
        private final Thread        acceptor;

        ConnectionRecordingServer() throws IOException {
            serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            acceptor     = Thread.ofVirtual().start(this::acceptConnections);
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        int connections() {
            return connections.get();
        }

        private void acceptConnections() {
            while (!serverSocket.isClosed()) {
                try (Socket socket = serverSocket.accept()) {
                    connections.incrementAndGet();
                    socket.setSoTimeout(2_000);
                    answer(socket);
                } catch (IOException e) {
                    // The listening socket was closed or a client went away; keep counting until
                    // closed.
                }
            }
        }

        private static void answer(Socket socket) throws IOException {
            val    reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String line;
            do {
                line = reader.readLine();
            } while (line != null && !line.isEmpty());
            socket.getOutputStream().write(RESPONSE.getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
            try {
                acceptor.join(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
