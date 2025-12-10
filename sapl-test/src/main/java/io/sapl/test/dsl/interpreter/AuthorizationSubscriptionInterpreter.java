/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.test.dsl.interpreter;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.test.SaplTestException;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class AuthorizationSubscriptionInterpreter {

    private final ValueInterpreter valueInterpreter;

    AuthorizationSubscription constructAuthorizationSubscription(
            io.sapl.test.grammar.sapltest.AuthorizationSubscription authorizationSubscription) {
        if (authorizationSubscription == null) {
            throw new SaplTestException("AuthorizationSubscription is null.");
        }

        var subject  = valueInterpreter.getValueFromDslValue(authorizationSubscription.getSubject());
        var action   = valueInterpreter.getValueFromDslValue(authorizationSubscription.getAction());
        var resource = valueInterpreter.getValueFromDslValue(authorizationSubscription.getResource());

        if (subject == null || action == null || resource == null) {
            throw new SaplTestException("subject or action or resource is null.");
        }

        var environmentValue = authorizationSubscription.getEnvironment();

        if (environmentValue == null) {
            return AuthorizationSubscription.of(subject, action, resource);
        }

        var environment = valueInterpreter.getValueFromDslValue(environmentValue);

        if (environment == null) {
            throw new SaplTestException("Environment is null.");
        }

        return AuthorizationSubscription.of(subject, action, resource, environment);
    }
}
