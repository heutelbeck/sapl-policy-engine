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
package io.sapl.attributes.broker.impl;

import io.sapl.attributes.broker.api.AttributeFinderSpecification;
import io.sapl.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttributeFinderSpecificationTests {

    private static final List<Validator> NO_VALIDATORS = List.of();

    @Test
    void whenConstructionOfPolicyInformationPointSpecificationHasBadParametersThenThrowElseDoNotThrow() {
        assertThatThrownBy(() -> new AttributeFinderSpecification(null, "a", true, 0, true, e -> {}, NO_VALIDATORS))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AttributeFinderSpecification("a", null, true, 0, true, e -> {}, NO_VALIDATORS))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void whenVarArgsCheckedThenVarArgsCorrectlyDetected() {
        var withVarArgs = new AttributeFinderSpecification("abc", "def", true,
                AttributeFinderSpecification.HAS_VARIABLE_NUMBER_OF_ARGUMENTS, true, e -> {}, NO_VALIDATORS);
        assertThat(withVarArgs.hasVariableNumberOfArguments()).isTrue();
        var notWithVarArgs = new AttributeFinderSpecification("abc", "def", true, 0, true, e -> {}, NO_VALIDATORS);
        assertThat(notWithVarArgs.hasVariableNumberOfArguments()).isFalse();
    }

}
