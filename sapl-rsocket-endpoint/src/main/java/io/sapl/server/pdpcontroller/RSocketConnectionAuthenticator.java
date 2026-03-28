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
package io.sapl.server.pdpcontroller;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import io.rsocket.ConnectionSetupPayload;
import reactor.core.publisher.Mono;

/**
 * Authenticates an RSocket connection from its setup frame metadata.
 * <p>
 * Implementations extract credentials from the RSocket
 * {@link ConnectionSetupPayload} (typically using
 * {@code io.rsocket.metadata.AuthMetadataCodec}) and validate them against the
 * configured user store.
 * <p>
 * Authentication happens once per connection. All subsequent requests on the
 * connection inherit the authenticated identity.
 * <p>
 * Returns an {@link AuthenticationResult} containing the PDP ID for
 * multi-tenant routing and an optional connection expiry time (from JWT
 * {@code exp} claim). On authentication failure, return a
 * {@code Mono.error()} which causes the connection to be rejected.
 */
@FunctionalInterface
public interface RSocketConnectionAuthenticator {

    /**
     * Authenticates the connection and returns the authentication result.
     *
     * @param setup the RSocket connection setup payload containing auth
     * metadata
     * @return a Mono emitting the authentication result on success, or an
     * error signal on authentication failure
     */
    Mono<AuthenticationResult> authenticate(ConnectionSetupPayload setup);

    /**
     * Result of a successful RSocket connection authentication.
     *
     * @param pdpId the PDP ID for multi-tenant routing
     * @param expiresAt when the credential expires (from JWT {@code exp}
     * claim), or null for non-expiring credentials (API key, basic auth)
     */
    record AuthenticationResult(String pdpId, @Nullable Instant expiresAt) {

        /**
         * Creates a result with no expiry (for API key or basic auth).
         *
         * @param pdpId the PDP ID for multi-tenant routing
         */
        AuthenticationResult(String pdpId) {
            this(pdpId, null);
        }
    }

}
