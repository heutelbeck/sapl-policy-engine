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
package io.sapl.spring.pep.http.reactive;

import static io.sapl.api.model.ValueJsonMarshaller.fromJsonNode;

import org.springframework.security.core.Authentication;
import org.springframework.web.server.ServerWebExchange;

import io.sapl.api.pdp.AuthorizationSubscription;
import lombok.RequiredArgsConstructor;
import lombok.val;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * Default {@link ReactiveAuthorizationSubscriptionFactory}: places the
 * resolved {@link Authentication} on {@code subject} and the serialized
 * {@link org.springframework.http.server.reactive.ServerHttpRequest} on
 * both {@code action} and {@code resource}, leaving {@code environment}
 * undefined. Registered automatically when no other
 * {@code ReactiveAuthorizationSubscriptionFactory} bean is present.
 */
@RequiredArgsConstructor
public class DefaultReactiveAuthorizationSubscriptionFactory implements ReactiveAuthorizationSubscriptionFactory {

    private final ObjectMapper mapper;

    @Override
    public Mono<AuthorizationSubscription> build(Authentication authentication, ServerWebExchange exchange) {
        val requestValue = fromJsonNode(mapper.valueToTree(exchange.getRequest()));
        return Mono.just(AuthorizationSubscription.of(authentication, requestValue, requestValue, mapper));
    }
}
