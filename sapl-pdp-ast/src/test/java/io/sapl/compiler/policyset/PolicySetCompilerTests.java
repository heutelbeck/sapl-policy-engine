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
package io.sapl.compiler.policyset;

import io.sapl.compiler.expressions.SaplCompilerException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.sapl.util.SaplTesting.compilePolicySet;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for PolicySetCompiler error paths including variable definition errors.
 * Note: Reserved identifiers (subject, action, resource, environment) are
 * rejected
 * at the parser level, so they don't reach the compiler's variable validation.
 */
@DisplayName("PolicySetCompiler")
class PolicySetCompilerTests {

    @Test
    @DisplayName("duplicate variable definition throws error")
    void duplicateVariableDefinitionThrowsError() {
        var policySet = """
                set "test"
                first-applicable

                var myVar = 1;
                var myVar = 2;

                policy "test"
                permit
                """;

        assertThatThrownBy(() -> compilePolicySet(policySet)).isInstanceOf(SaplCompilerException.class)
                .hasMessageContaining("Redefinition of variable myVar not permitted");
    }

}
