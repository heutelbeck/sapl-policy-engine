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
package io.sapl.grammar.ide.contentassist;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.xtext.testing.TestCompletionConfiguration;
import org.junit.jupiter.api.Test;

class IdeStepCompletionTests extends CompletionTests {

    @Test
    void testCompletion_EmptyAttributeStepReturnsClockFunctions() {
        testCompletion((TestCompletionConfiguration it) -> {
            String policy = "policy \"test\" permit where subject.<";
            it.setModel(policy);
            it.setColumn(policy.length());
            it.setAssertCompletionList(completionList -> {
                var expected = List.of("clock.millis>", "clock.now>", "clock.ticker>", "temperature.mean(a1, a2)>",
                        "temperature.mean(a1, a2)>.period", "temperature.mean(a1, a2)>.value", "temperature.now()>",
                        "temperature.now()>.unit", "temperature.now()>.value", "temperature.predicted(a2)>");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_AttributeStepWithPrefixReturnsMatchingClockFunction() {
        testCompletion((TestCompletionConfiguration it) -> {
            String policy = "policy \"test\" permit where subject.<clock.n";
            it.setModel(policy);
            it.setColumn(policy.length());
            it.setAssertCompletionList(completionList -> {
                var expected = List.of("clock.now>");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_AttributeStepWithNoMatchingPrefixReturnsNoMatchingFunction() {
        testCompletion((TestCompletionConfiguration it) -> {
            String policy = "policy \"test\" permit where subject.<foo";
            it.setModel(policy);
            it.setColumn(policy.length());
            it.setAssertCompletionList(completionList -> {
                var expected = new ArrayList<String>();
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_HeadEmptyAttributeStepReturnsClockFunctions() {
        testCompletion((TestCompletionConfiguration it) -> {
            String policy = "policy \"test\" permit where subject.|<";
            it.setModel(policy);
            it.setColumn(policy.length());
            it.setAssertCompletionList(completionList -> {
                var expected = List.of("clock.millis>", "clock.now>", "clock.ticker>", "temperature.mean(a1, a2)>",
                        "temperature.mean(a1, a2)>.period", "temperature.mean(a1, a2)>.value", "temperature.now()>",
                        "temperature.now()>.unit", "temperature.now()>.value", "temperature.predicted(a2)>");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_HeadAttributeStepWithPrefixReturnsMatchingClockFunction() {
        testCompletion((TestCompletionConfiguration it) -> {
            String policy = "policy \"test\" permit where subject.|<clock.n";
            it.setModel(policy);
            it.setColumn(policy.length());
            it.setAssertCompletionList(completionList -> {
                var expected = List.of("clock.now>");
                assertProposalsSimple(expected, completionList);
            });
        });
    }
    @Test
    void testCompletion_HeadAttributeStepWithNonReservedPrefixReturnsMatchingFunction() {
        testCompletion((TestCompletionConfiguration it) -> {
            String policy = "policy \"test\" permit where foo.|<clock.n";
            it.setModel(policy);
            it.setColumn(policy.length());
            it.setAssertCompletionList(completionList -> {
                var expected = List.of("clock.now>");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

/*    @Test
    void testCompletion_HeadAttributeStepWithNoMatchingPrefixReturnsNoMatchingFunction() {
        testCompletion((TestCompletionConfiguration it) -> {
            String policy = "policy \"test\" permit where subject.|<";
            it.setModel(policy);
            it.setColumn(policy.length());
            it.setAssertCompletionList(completionList -> {
                var expected = new ArrayList<String>();
                assertProposalsSimple(expected, completionList);
            });
        });
    }*/

}
