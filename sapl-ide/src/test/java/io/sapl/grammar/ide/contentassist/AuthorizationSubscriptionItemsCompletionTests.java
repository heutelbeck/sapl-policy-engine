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
package io.sapl.grammar.ide.contentassist;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * Tests regarding the auto-completion of the keywords subject, resource,
 * action, environment
 */
@SpringBootTest
@ContextConfiguration(classes = SAPLIdeSpringTestConfiguration.class)
class AuthorizationSubscriptionItemsCompletionTests extends CompletionTests {

    @Test
    void testCompletion_AuthorizationSubscriptionItemsInTargetExpression1() {
        final var document = "policy \"test\" permit a§";
        final var expected = List.of("action");
        assertProposalsContain(document, expected);

    }

    @Test
    void testCompletion_AuthorizationSubscriptionItemsInTargetExpression2() {
        final var document = "policy \"test\" permit e§";
        final var expected = List.of("environment");
        assertProposalsContain(document, expected);

    }

    @Test
    void testCompletion_AuthorizationSubscriptionItemsInTargetExpression3() {
        final var document = "policy \"test\" permit r§";
        final var expected = List.of("resource");
        assertProposalsContain(document, expected);

    }

    @Test
    void testCompletion_AuthorizationSubscriptionItemsInTargetExpression4() {
        final var document = "policy \"test\" permit s§";
        final var expected = List.of("subject");
        assertProposalsContain(document, expected);

    }

    @Test
    void testCompletion_AuthorizationSubscriptionItemsInBody0() {
        final var document = "policy \"test\" permit where §";
        final var expected = List.of("var");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_AuthorizationSubscriptionItemsInBody1() {
        final var document = "policy \"test\" permit where a§";
        final var expected = List.of("action");
        assertProposalsContain(document, expected);

    }

    @Test
    void testCompletion_AuthorizationSubscriptionItemsInBody2() {
        final var document = "policy \"test\" permit where e§";
        final var expected = List.of("environment");
        assertProposalsContain(document, expected);

    }

    @Test
    void testCompletion_AuthorizationSubscriptionItemsInBody3() {
        final var document = "policy \"test\" permit where r§";
        final var expected = List.of("resource");
        assertProposalsContain(document, expected);

    }

    @Test
    void testCompletion_AuthorizationSubscriptionItemsInBody4() {
        final var document = "policy \"test\" permit where s§";
        final var expected = List.of("subject");
        assertProposalsContain(document, expected);

    }

    @Test
    void testCompletion_NoTechnicalProposalsAfterAuthorizationSubscriptionItem() {
        final var document = "policy \"test\" permit where subject.§";
        final var unwanted = List.of("\"id\"", "id", "idSteps");
        assertProposalsDoNotContain(document, unwanted);

    }

    @Test
    void testCompletion_SuggestAttributesForEnvironmentalAttribute() {
        final var document = "policy \"test\" permit where <c§";
        final var expected = List.of("clock.millis(personLeftHand)>", "clock.now>", "clock.ticker>");
        assertProposalsContain(document, expected);

    }

    @Test
    void testCompletion_SuggestAttributesForHeadEnvironmentalAttribute() {
        final var document = "policy \"test\" permit where |<c§";
        final var expected = List.of("clock.millis(personLeftHand)>", "clock.now>", "clock.ticker>");
        assertProposalsContain(document, expected);

    }

}
