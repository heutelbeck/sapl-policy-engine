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
package io.sapl.pdp.remote;

/**
 * Factory for creating remote Policy Decision Point clients.
 * <p>
 * Use the fluent builder API to configure and create a remote PDP connection:
 *
 * <pre>{@code
 * var pdp = RemotePolicyDecisionPoint.builder().http().baseUrl("https://localhost:8443")
 *         .basicAuth("clientKey", "clientSecret").build();
 * }</pre>
 */
public class RemotePolicyDecisionPoint {

    /**
     * Creates a new builder for configuring a remote PDP connection.
     *
     * @return a new builder instance
     */
    public static RemotePolicyDecisionPoint builder() {
        return new RemotePolicyDecisionPoint();
    }

    /**
     * Configures the remote PDP to use HTTP/HTTPS transport.
     *
     * @return an HTTP-specific builder for further configuration
     */
    public RemoteHttpPolicyDecisionPoint.RemoteHttpPolicyDecisionPointBuilder http() {
        return RemoteHttpPolicyDecisionPoint.builder();
    }

}
