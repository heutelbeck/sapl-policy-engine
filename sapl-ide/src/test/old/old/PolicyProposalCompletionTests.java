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
package io.sapl.grammar.ide.contentassist.old;

import java.util.List;

import org.eclipse.xtext.testing.TestCompletionConfiguration;
import org.junit.jupiter.api.Test;

class PolicyProposalCompletionTests extends CompletionTests {

    @Test
    void testCompletion_PolicyNameIsEmptyString() {
        testCompletion((TestCompletionConfiguration it) -> {
            String policy = "policy ";
            it.setModel(policy);
            it.setColumn(policy.length());
            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("\"\"");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

}
