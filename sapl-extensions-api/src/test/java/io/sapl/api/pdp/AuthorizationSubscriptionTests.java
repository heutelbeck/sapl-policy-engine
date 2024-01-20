/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.api.pdp;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

import org.junit.jupiter.api.Test;

class AuthorizationSubscriptionTests {

    private static final String ENVIRONMENT = "ENVIRONMENT";
    private static final String RESOURCE    = "RESOURCE";
    private static final String ACTION      = "ACTION";
    private static final String SUBJECT     = "SUBJECT";

    @Test
    void subjectActionResourceDefaultMapper() {
        var subscription = AuthorizationSubscription.of(SUBJECT, ACTION, RESOURCE);
        assertThatJson(subscription.getSubject()).isString().isEqualTo(SUBJECT);
        assertThatJson(subscription.getAction()).isString().isEqualTo(ACTION);
        assertThatJson(subscription.getResource()).isString().isEqualTo(RESOURCE);
        assertThatJson(subscription.getEnvironment()).isNull();
    }

    @Test
    void subjectActionResourceEnvironmentDefaultMapper() {
        var subscription = AuthorizationSubscription.of(SUBJECT, ACTION, RESOURCE, ENVIRONMENT);
        assertThatJson(subscription.getSubject()).isString().isEqualTo(SUBJECT);
        assertThatJson(subscription.getAction()).isString().isEqualTo(ACTION);
        assertThatJson(subscription.getResource()).isString().isEqualTo(RESOURCE);
        assertThatJson(subscription.getEnvironment()).isEqualTo(ENVIRONMENT);
    }

}
