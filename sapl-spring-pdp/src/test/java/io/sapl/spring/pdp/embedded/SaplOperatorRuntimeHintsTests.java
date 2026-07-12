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
package io.sapl.spring.pdp.embedded;

import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SaplOperatorRuntimeHints")
class SaplOperatorRuntimeHintsTests {

    // The operator record whose missing reflection registration broke the native
    // image when building an index over a non-trivial policy set.
    private static final String KEY_STEP_PURE = "io.sapl.compiler.expressions.StepCompiler$KeyStepPure";

    @Test
    @DisplayName("discovers the PureOperator records, including the one that broke the native image")
    void whenScanningThenAllPureOperatorRecordsAreDiscovered() {
        val discovered = SaplOperatorRuntimeHints.discoverOperatorTypeNames();
        assertThat(discovered).contains(KEY_STEP_PURE).hasSizeGreaterThan(20);
    }

    @Test
    @DisplayName("registers method reflection for every discovered operator record")
    void whenRegisteringThenEachOperatorRecordHasMethodReflectionHints() {
        val hints = new RuntimeHints();
        new SaplOperatorRuntimeHints().registerHints(hints, getClass().getClassLoader());
        assertThat(RuntimeHintsPredicates.reflection().onType(TypeReference.of(KEY_STEP_PURE))).accepts(hints);
    }
}
