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
package io.sapl.spring.subscriptions;

import io.sapl.api.model.ObjectValue;
import org.springframework.security.core.Authentication;

/**
 * Strategy interface for injecting secrets into authorization subscriptions.
 * <p>
 * Implementations extract credential material from the authentication context
 * and return it as an ObjectValue to be merged into subscription secrets.
 * <p>
 * When an explicit secrets SpEL expression is provided in the annotation, the
 * SpEL expression takes precedence and the injector result is ignored.
 */
@FunctionalInterface
public interface SubscriptionSecretsInjector {

    /**
     * Extracts secrets from the given authentication to inject into
     * authorization subscriptions.
     *
     * @param authentication the current authentication
     * @return an ObjectValue containing secrets to merge, or
     * {@link io.sapl.api.model.Value#EMPTY_OBJECT} if no secrets
     * should be injected
     */
    ObjectValue injectSecrets(Authentication authentication);

}
