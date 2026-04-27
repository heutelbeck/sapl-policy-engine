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
package io.sapl.spring.pep.http.servlet;

import org.springframework.security.core.Authentication;

import io.sapl.api.pdp.AuthorizationSubscription;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Builds the {@link AuthorizationSubscription} that
 * {@link SaplAuthorizationManager} sends to the PDP for one HTTP request.
 * <p>
 * The default implementation
 * ({@link DefaultAuthorizationSubscriptionFactory}) places the resolved
 * {@link Authentication} on {@code subject} and the serialized request on
 * both {@code action} and {@code resource}, leaving {@code environment}
 * undefined. Applications that want a narrower or differently shaped
 * subscription register their own factory bean of this type, or pass an
 * inline factory to the SAPL HTTP security configurer:
 *
 * <pre>{@code
 * http.with(saplHttp(), c -> c.subscriptionFactory(
 *         (auth, req) -> AuthorizationSubscription.of(auth.getName(), req.getMethod(), req.getRequestURI(), mapper)));
 * }</pre>
 * <p>
 * The factory is invoked once per request from inside the authorization
 * manager. The {@link Authentication} passed in is never {@code null};
 * an anonymous token is supplied when the security context has no
 * authentication.
 */
@FunctionalInterface
public interface AuthorizationSubscriptionFactory {

    /**
     * Returns the {@link AuthorizationSubscription} to send to the PDP.
     *
     * @param authentication the resolved authentication (anonymous token
     * when the security context had no authentication).
     * @param request the inbound HTTP request.
     */
    AuthorizationSubscription build(Authentication authentication, HttpServletRequest request);
}
