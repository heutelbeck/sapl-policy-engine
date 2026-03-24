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
 * Returns the PDP ID for multi-tenant routing. On authentication failure,
 * return a {@code Mono.error()} which causes the connection to be rejected.
 */
@FunctionalInterface
public interface RSocketConnectionAuthenticator {

    /**
     * Authenticates the connection and returns the PDP ID for tenant routing.
     *
     * @param setup the RSocket connection setup payload containing auth
     * metadata
     * @return a Mono emitting the PDP ID on success, or an error signal on
     * authentication failure
     */
    Mono<String> authenticate(ConnectionSetupPayload setup);

}
