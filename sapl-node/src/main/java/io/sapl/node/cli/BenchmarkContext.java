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
 * @param remoteUrl remote PDP URL (null for embedded)
 * @param basicAuth HTTP Basic credentials as user:password (null if unused)
 * @param token bearer token for API key or JWT (null if unused)
 * @param insecure skip TLS certificate verification
 */
record BenchmarkContext(
        String subscriptionJson,
        @Nullable String policiesPath,
        @Nullable String configType,
        @Nullable String remoteUrl,
        @Nullable String basicAuth,
        @Nullable String token,
        boolean insecure) {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    static BenchmarkContext embedded(String subscriptionJson, String policiesPath, String configType) {
        return new BenchmarkContext(subscriptionJson, policiesPath, configType, null, null, null, false);
    }

    static BenchmarkContext remote(String subscriptionJson, String remoteUrl, @Nullable String basicAuth,
            @Nullable String token, boolean insecure) {
        return new BenchmarkContext(subscriptionJson, null, null, remoteUrl, basicAuth, token, insecure);
    }

    boolean isRemote() {
        return remoteUrl != null;
    }

    String toJson() {
        return MAPPER.writeValueAsString(this);
    }

    static BenchmarkContext fromJson(String json) {
        return MAPPER.readValue(json, BenchmarkContext.class);
    }

}
