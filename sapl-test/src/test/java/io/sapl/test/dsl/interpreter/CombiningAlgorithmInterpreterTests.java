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

package io.sapl.test.dsl.interpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.grammar.sapl.CombiningAlgorithm;
import io.sapl.test.SaplTestException;
import io.sapl.test.grammar.sapltest.CombiningAlgorithmEnum;

class CombiningAlgorithmInterpreterTests {
    protected CombiningAlgorithmInterpreter combiningAlgorithmInterpreter;

    @BeforeEach
    void setUp() {
        combiningAlgorithmInterpreter = new CombiningAlgorithmInterpreter();
    }

    @Test
    void interpretPdpCombiningAlgorithm_handlesNullCombiningAlgorithm_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class,
                () -> combiningAlgorithmInterpreter.interpretPdpCombiningAlgorithm(null));

        assertEquals("CombiningAlgorithm is null", exception.getMessage());
    }

    private static Stream<Arguments> combiningAlgorithmEnumToPolicyDocumentCombiningAlgorithm() {
        return Stream.of(Arguments.of(CombiningAlgorithmEnum.DENY_OVERRIDES, CombiningAlgorithm.DENY_OVERRIDES),
                Arguments.of(CombiningAlgorithmEnum.PERMIT_OVERRIDES, CombiningAlgorithm.PERMIT_OVERRIDES),
                Arguments.of(CombiningAlgorithmEnum.ONLY_ONE_APPLICABLE, CombiningAlgorithm.ONLY_ONE_APPLICABLE),
                Arguments.of(CombiningAlgorithmEnum.DENY_UNLESS_PERMIT, CombiningAlgorithm.DENY_UNLESS_PERMIT),
                Arguments.of(CombiningAlgorithmEnum.PERMIT_UNLESS_DENY, CombiningAlgorithm.PERMIT_UNLESS_DENY));
    }

    @ParameterizedTest
    @MethodSource("combiningAlgorithmEnumToPolicyDocumentCombiningAlgorithm")
    void interpretPdpCombiningAlgorithm_handlesGivenCombiningAlgorithm_returnsPolicyDocumentCombiningAlgorithm(
            final CombiningAlgorithmEnum combiningAlgorithm, final CombiningAlgorithm expectedCombiningAlgorithm) {
        final var result = combiningAlgorithmInterpreter.interpretPdpCombiningAlgorithm(combiningAlgorithm);

        assertEquals(expectedCombiningAlgorithm, result);
    }
}
