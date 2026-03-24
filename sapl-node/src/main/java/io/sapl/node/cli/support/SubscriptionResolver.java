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
package io.sapl.node.cli.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.node.cli.options.NamedSubscriptionOptions;
import io.sapl.node.cli.options.SubscriptionInputOptions;
import lombok.experimental.UtilityClass;
import lombok.val;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@UtilityClass
public class SubscriptionResolver {

    private static final Path STDIN_MARKER = Path.of("-");

    private static final String ERROR_INVALID_JSON_VALUE          = "Error: Invalid JSON value: %s.";
    private static final String ERROR_READING_STDIN               = "Error: Failed to read subscription from stdin: %s.";
    private static final String ERROR_READING_SUBSCRIPTION_FILE   = "Error: Failed to read subscription file: %s.";
    private static final String ERROR_SECRETS_NOT_OBJECT          = "Error: --secrets value must be a JSON object.";
    private static final String ERROR_SUBSCRIPTION_FILE_NOT_FOUND = "Error: Subscription file not found: %s.";
    private static final String ERROR_SUBSCRIPTION_INVALID_JSON   = "Error: Invalid JSON in subscription: %s.";
    private static final String ERROR_SUBSCRIPTION_REQUIRED       = "Error: No subscription provided. Use -s/-a/-r flags or -f <file> (-f - for stdin).";

    public static AuthorizationSubscription resolve(SubscriptionInputOptions subscriptionInput, JsonMapper mapper) {
        if (subscriptionInput != null) {
            return buildSubscription(subscriptionInput, mapper);
        }
        throw new IllegalArgumentException(ERROR_SUBSCRIPTION_REQUIRED);
    }

    private static AuthorizationSubscription buildSubscription(SubscriptionInputOptions subscriptionInput,
            JsonMapper mapper) {
        if (subscriptionInput.named != null) {
            return buildNamedSubscription(subscriptionInput.named, mapper);
        }
        return buildFileSubscription(subscriptionInput.file, mapper);
    }

    private static AuthorizationSubscription buildNamedSubscription(NamedSubscriptionOptions named, JsonMapper mapper) {
        val subject     = parseJson(mapper, named.subject);
        val action      = parseJson(mapper, named.action);
        val resource    = parseJson(mapper, named.resource);
        val environment = named.environment != null ? parseJson(mapper, named.environment) : null;
        val secrets     = named.secrets != null ? parseSecretsJson(mapper, named.secrets) : null;
        return AuthorizationSubscription.of(subject, action, resource, environment, secrets, mapper);
    }

    private static AuthorizationSubscription buildFileSubscription(Path file, JsonMapper mapper) {
        if (STDIN_MARKER.equals(file)) {
            return readSubscriptionFromStdin(mapper);
        }
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException(ERROR_SUBSCRIPTION_FILE_NOT_FOUND.formatted(file));
        }
        try {
            val content = Files.readString(file);
            return deserializeSubscription(mapper, content);
        } catch (IOException e) {
            throw new IllegalArgumentException(ERROR_READING_SUBSCRIPTION_FILE.formatted(e.getMessage()), e);
        }
    }

    private static AuthorizationSubscription readSubscriptionFromStdin(JsonMapper mapper) {
        try {
            val content = new String(System.in.readAllBytes(), StandardCharsets.UTF_8).trim();
            return deserializeSubscription(mapper, content);
        } catch (IOException e) {
            throw new IllegalArgumentException(ERROR_READING_STDIN.formatted(e.getMessage()), e);
        }
    }

    private static JsonNode parseJson(JsonMapper mapper, String value) {
        try {
            return mapper.readTree(value);
        } catch (JacksonException e) {
            throw new IllegalArgumentException(ERROR_INVALID_JSON_VALUE.formatted(value), e);
        }
    }

    private static JsonNode parseSecretsJson(JsonMapper mapper, String value) {
        val node = parseJson(mapper, value);
        if (!node.isObject()) {
            throw new IllegalArgumentException(ERROR_SECRETS_NOT_OBJECT);
        }
        return node;
    }

    private static AuthorizationSubscription deserializeSubscription(JsonMapper mapper, String json) {
        try {
            return mapper.readValue(json, AuthorizationSubscription.class);
        } catch (JacksonException e) {
            throw new IllegalArgumentException(ERROR_SUBSCRIPTION_INVALID_JSON.formatted(e.getMessage()), e);
        }
    }

}
