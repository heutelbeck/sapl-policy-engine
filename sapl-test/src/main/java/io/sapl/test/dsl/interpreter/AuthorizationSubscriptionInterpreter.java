/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

    private final ValInterpreter valInterpreter;

    AuthorizationSubscription constructAuthorizationSubscription(
            final io.sapl.test.grammar.sAPLTest.AuthorizationSubscription authorizationSubscription) {
        if (authorizationSubscription == null) {
            throw new SaplTestException("AuthorizationSubscription is null");
        }

        final var subject  = valInterpreter.getValFromValue(authorizationSubscription.getSubject());
        final var action   = valInterpreter.getValFromValue(authorizationSubscription.getAction());
        final var resource = valInterpreter.getValFromValue(authorizationSubscription.getResource());

        final var environmentValue = authorizationSubscription.getEnvironment();

        if (environmentValue == null) {
            return AuthorizationSubscription.of(subject.get(), action.get(), resource.get());
        }

        final var environment = valInterpreter.getValFromValue(environmentValue);

        return AuthorizationSubscription.of(subject.get(), action.get(), resource.get(), environment.get());
    }
}
