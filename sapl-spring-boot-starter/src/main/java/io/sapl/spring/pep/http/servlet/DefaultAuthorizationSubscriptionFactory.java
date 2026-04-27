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

import static io.sapl.api.model.ValueJsonMarshaller.fromJsonNode;

import org.springframework.security.core.Authentication;

import io.sapl.api.pdp.AuthorizationSubscription;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.val;
import tools.jackson.databind.ObjectMapper;

/**
 * Default {@link AuthorizationSubscriptionFactory}: places the resolved
 * {@link Authentication} on {@code subject} and the serialized
 * {@link HttpServletRequest} on both {@code action} and {@code resource},
 * leaving {@code environment} undefined. Registered automatically when no
 * other {@code AuthorizationSubscriptionFactory} bean is present.
 */
@RequiredArgsConstructor
public class DefaultAuthorizationSubscriptionFactory implements AuthorizationSubscriptionFactory {

    private final ObjectMapper mapper;

    @Override
    public AuthorizationSubscription build(Authentication authentication, HttpServletRequest request) {
        val requestValue = fromJsonNode(mapper.valueToTree(request));
        return AuthorizationSubscription.of(authentication, requestValue, requestValue, mapper);
    }
}
