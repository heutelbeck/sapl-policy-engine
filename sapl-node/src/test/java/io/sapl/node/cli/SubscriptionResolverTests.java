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
package io.sapl.node.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.sapl.api.model.jackson.SaplJacksonModule;
import lombok.val;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("subscription resolver")
class SubscriptionResolverTests {

    private static final JsonMapper MAPPER = JsonMapper.builder().addModule(new SaplJacksonModule()).build();

    @Nested
    @DisplayName("named subscription")
    class NamedSubscriptionTests {

        @Test
        @DisplayName("JSON string values create valid subscription")
        void whenJsonStringValues_thenSubscriptionCreated() {
            val input        = inputWithNamed("\"alice\"", "\"read\"", "\"document\"");
            val subscription = SubscriptionResolver.resolve(input, MAPPER);
            val json         = MAPPER.writeValueAsString(subscription);
            assertThat(json).contains("\"subject\":\"alice\"", "\"action\":\"read\"", "\"resource\":\"document\"");
        }

        @Test
        @DisplayName("JSON object values create valid subscription")
        void whenJsonObjectValues_thenSubscriptionCreated() {
            val input        = inputWithNamed("{\"name\":\"alice\"}", "\"read\"", "{\"type\":\"doc\",\"id\":42}");
            val subscription = SubscriptionResolver.resolve(input, MAPPER);
            val json         = MAPPER.writeValueAsString(subscription);
            assertThat(json).contains("\"name\":\"alice\"", "\"type\":\"doc\"");
        }

        @Test
        @DisplayName("JSON number value is parsed as number")
        void whenJsonNumberValue_thenParsedAsNumber() {
            val input        = inputWithNamed("42", "\"read\"", "\"doc\"");
            val subscription = SubscriptionResolver.resolve(input, MAPPER);
            val json         = MAPPER.writeValueAsString(subscription);
            assertThat(json).contains("\"subject\":42");
        }

        @Test
        @DisplayName("invalid JSON value throws IllegalArgumentException")
        void whenInvalidJsonValue_thenThrowsIllegalArgument() {
            val input = inputWithNamed("not-valid-json", "\"read\"", "\"doc\"");
            assertThatThrownBy(() -> SubscriptionResolver.resolve(input, MAPPER))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Invalid JSON value");
        }

        @Test
        @DisplayName("secrets that are not a JSON object throw IllegalArgumentException")
        void whenSecretsNotObject_thenThrowsIllegalArgument() {
            val input = inputWithNamed("\"alice\"", "\"read\"", "\"doc\"");
            input.named.secrets = "\"not-an-object\"";
            assertThatThrownBy(() -> SubscriptionResolver.resolve(input, MAPPER))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("JSON object");
        }

        @Test
        @DisplayName("valid secrets JSON object is accepted")
        void whenSecretsObject_thenSubscriptionCreated() {
            val input = inputWithNamed("\"alice\"", "\"read\"", "\"doc\"");
            input.named.secrets = "{\"key\":\"value\"}";
            val subscription = SubscriptionResolver.resolve(input, MAPPER);
            assertThat(subscription).isNotNull();
        }

        @Test
        @DisplayName("environment is included when provided")
        void whenEnvironmentProvided_thenIncludedInSubscription() {
            val input = inputWithNamed("\"alice\"", "\"read\"", "\"doc\"");
            input.named.environment = "{\"time\":\"morning\"}";
            val subscription = SubscriptionResolver.resolve(input, MAPPER);
            val json         = MAPPER.writeValueAsString(subscription);
            assertThat(json).contains("\"time\":\"morning\"");
        }

    }

    @Nested
    @DisplayName("file subscription")
    class FileSubscriptionTests {

        @Test
        @DisplayName("file subscription is deserialized via SaplJacksonModule")
        void whenFileSubscription_thenDeserialized(@TempDir Path tempDir) throws IOException {
            val file = tempDir.resolve("request.json");
            Files.writeString(file, """
                    {"subject":"alice","action":"read","resource":"document"}
                    """);
            val input        = inputWithFile(file);
            val subscription = SubscriptionResolver.resolve(input, MAPPER);
            val json         = MAPPER.writeValueAsString(subscription);
            assertThat(json).contains("\"subject\":\"alice\"", "\"action\":\"read\"", "\"resource\":\"document\"");
        }

        @Test
        @DisplayName("missing subscription file throws IllegalArgumentException")
        void whenFileMissing_thenThrowsIllegalArgument() {
            val input = inputWithFile(Path.of("/nonexistent/file.json"));
            assertThatThrownBy(() -> SubscriptionResolver.resolve(input, MAPPER))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Subscription file not found");
        }

        @Test
        @DisplayName("invalid JSON in file throws IllegalArgumentException")
        void whenFileContainsInvalidJson_thenThrowsIllegalArgument(@TempDir Path tempDir) throws IOException {
            val file = tempDir.resolve("bad.json");
            Files.writeString(file, "not valid json at all");
            val input = inputWithFile(file);
            assertThatThrownBy(() -> SubscriptionResolver.resolve(input, MAPPER))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Invalid JSON");
        }

        @Test
        @DisplayName("file missing required fields throws IllegalArgumentException")
        void whenFileMissingRequiredFields_thenThrowsIllegalArgument(@TempDir Path tempDir) throws IOException {
            val file = tempDir.resolve("incomplete.json");
            Files.writeString(file, "{\"subject\":\"alice\"}");
            val input = inputWithFile(file);
            assertThatThrownBy(() -> SubscriptionResolver.resolve(input, MAPPER))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Invalid JSON");
        }

        @Test
        @DisplayName("-f - reads subscription from stdin")
        void whenStdinMarker_thenReadsFromStdin() {
            val json       = "{\"subject\":\"alice\",\"action\":\"read\",\"resource\":\"document\"}";
            val originalIn = System.in;
            try {
                System.setIn(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
                val input        = inputWithFile(Path.of("-"));
                val subscription = SubscriptionResolver.resolve(input, MAPPER);
                val output       = MAPPER.writeValueAsString(subscription);
                assertThat(output).contains("\"subject\":\"alice\"", "\"action\":\"read\"",
                        "\"resource\":\"document\"");
            } finally {
                System.setIn(originalIn);
            }
        }

        @Test
        @DisplayName("-f - with invalid JSON from stdin throws IllegalArgumentException")
        void whenStdinInvalidJson_thenThrowsIllegalArgument() {
            val originalIn = System.in;
            try {
                System.setIn(new ByteArrayInputStream("not json".getBytes(StandardCharsets.UTF_8)));
                val input = inputWithFile(Path.of("-"));
                assertThatThrownBy(() -> SubscriptionResolver.resolve(input, MAPPER))
                        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Invalid JSON");
            } finally {
                System.setIn(originalIn);
            }
        }

    }

    @Nested
    @DisplayName("resolve")
    class ResolveTests {

        @Test
        @DisplayName("null input throws IllegalArgumentException with subscription required message")
        void whenNullInput_thenThrowsSubscriptionRequired() {
            assertThatThrownBy(() -> SubscriptionResolver.resolve(null, MAPPER))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("No subscription provided");
        }

    }

    private static SubscriptionInputOptions inputWithNamed(String subject, String action, String resource) {
        val input = new SubscriptionInputOptions();
        val named = new NamedSubscriptionOptions();
        named.subject  = subject;
        named.action   = action;
        named.resource = resource;
        input.named    = named;
        return input;
    }

    private static SubscriptionInputOptions inputWithFile(Path file) {
        val input = new SubscriptionInputOptions();
        input.file = file;
        return input;
    }

}
