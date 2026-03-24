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

import org.jspecify.annotations.Nullable;

import tools.jackson.databind.json.JsonMapper;

/**
 * Serializable context for passing benchmark configuration to JMH via
 * {@code @Param} or directly to the native benchmark runner.
 * <p>
 * For embedded mode, {@code policiesPath} and {@code configType} are set.
 * For remote mode, {@code remoteUrl} is set and policy fields are null.
 *
 * @param subscriptionJson the authorization subscription as a JSON string
 * @param policiesPath absolute path to the policy directory or bundle (null for
 * remote)
 * @param configType DIRECTORY or BUNDLES (null for remote)
 * @param remoteUrl remote PDP URL for HTTP (null for embedded or rsocket)
 * @param basicAuth HTTP Basic credentials as user:password (null if unused)
 * @param token bearer token for API key or JWT (null if unused)
 * @param insecure skip TLS certificate verification
 * @param rsocket true to use RSocket/protobuf transport
 * @param rsocketHost RSocket host (null for embedded or HTTP)
 * @param rsocketPort RSocket port (0 for embedded or HTTP)
 */
public record BenchmarkContext(
        String subscriptionJson,
        @Nullable String policiesPath,
        @Nullable String configType,
        @Nullable String remoteUrl,
        @Nullable String basicAuth,
        @Nullable String token,
        boolean insecure,
        boolean rsocket,
        @Nullable String rsocketHost,
        int rsocketPort) {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    public static BenchmarkContext embedded(String subscriptionJson, String policiesPath, String configType) {
        return new BenchmarkContext(subscriptionJson, policiesPath, configType, null, null, null, false, false, null,
                0);
    }

    public static BenchmarkContext remote(String subscriptionJson, String remoteUrl, @Nullable String basicAuth,
            @Nullable String token, boolean insecure) {
        return new BenchmarkContext(subscriptionJson, null, null, remoteUrl, basicAuth, token, insecure, false, null,
                0);
    }

    public static BenchmarkContext rsocket(String subscriptionJson, String host, int port, @Nullable String basicAuth,
            @Nullable String token) {
        return new BenchmarkContext(subscriptionJson, null, null, null, basicAuth, token, false, true, host, port);
    }

    public boolean isRemote() {
        return remoteUrl != null || rsocket;
    }

    public String toJson() {
        return MAPPER.writeValueAsString(this);
    }

    public static BenchmarkContext fromJson(String json) {
        return MAPPER.readValue(json, BenchmarkContext.class);
    }

}
