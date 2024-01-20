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
package io.sapl.grammar.ide.contentassist;

import java.util.List;

import org.eclipse.xtext.testing.TestCompletionConfiguration;
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
    void testCompletion_AuthorizationSubscriptionItemsInTargetExpression() {
        testCompletion((TestCompletionConfiguration it) -> {
            String policy = "policy \"test\" permit ";
            it.setModel(policy);
            it.setColumn(policy.length());
            it.setAssertCompletionList(completionList -> {
                var expected = List.of("advice", "obligation", "transform", "where", "action", "environment",
                        "resource", "subject");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_AuthorizationSubscriptionItemsInBody() {
        testCompletion((TestCompletionConfiguration it) -> {
            String policy = "policy \"test\" permit where ";
            it.setModel(policy);
            it.setColumn(policy.length());
            it.setAssertCompletionList(completionList -> {
                var expected = List.of("var", "action", "environment", "resource", "subject");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_NoTechnicalProposalsAfterAuthorizationSubscriptionItem() {
        testCompletion((TestCompletionConfiguration it) -> {
            String policy = "policy \"test\" permit where subject.";
            it.setModel(policy);
            it.setColumn(policy.length());
            it.setAssertCompletionList(completionList -> {
                var unwantedProposals = List.of("\"id\"", "id", "idSteps");
                assertDoesNotContainProposals(unwantedProposals, completionList);
            });
        });
    }

    @Test
    void testCompletion_SuggestAttributesForEnvironmentalAttribute() {
        testCompletion((TestCompletionConfiguration it) -> {
            String policy = "policy \"test\" permit where <";
            it.setModel(policy);
            it.setColumn(policy.length());
            it.setAssertCompletionList(completionList -> {
                var expectedProposals = List.of("<clock.now>", "<clock.ticker>", "<clock.millis>");
                assertProposalsSimple(expectedProposals, completionList);
            });
        });
    }

    @Test
    void testCompletion_SuggestAttributesForHeadEnvironmentalAttribute() {
        testCompletion((TestCompletionConfiguration it) -> {
            String policy = "policy \"test\" permit where |<";
            it.setModel(policy);
            it.setColumn(policy.length());
            it.setAssertCompletionList(completionList -> {
                var expectedProposals = List.of("<clock.now>", "<clock.ticker>", "<clock.millis>");
                assertProposalsSimple(expectedProposals, completionList);
            });
        });
    }

}
