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
package io.sapl.broker.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.lang.annotation.Annotation;
import java.util.List;

import org.junit.jupiter.api.Test;

class PolicyInformationPointSpecificationTests {

    @Test
    void whenConstructionOfPolicyInformationPointSpecificationHasBadParametersThenThrowElseDoNotThrow() {
        final List<Annotation>       entityValidators    = List.of();
        final List<List<Annotation>> parameterValidators = List.of();
        assertThatThrownBy(() -> new PolicyInformationPointSpecification(null, true, 0, true, entityValidators,
                parameterValidators)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                () -> new PolicyInformationPointSpecification("abc.def", true, 0, true, null, parameterValidators))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                () -> new PolicyInformationPointSpecification("abc.def", true, 0, true, entityValidators, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PolicyInformationPointSpecification(" abc.def ", true, 0, true, entityValidators,
                parameterValidators)).isInstanceOf(IllegalArgumentException.class);
        assertDoesNotThrow(() -> new PolicyInformationPointSpecification("abc.def", true, 0, true, entityValidators,
                parameterValidators));
    }

    @Test
    void whenVarArgsCheckedThenVarArgsCorrectlyDetected() {
        var withVarArgs = new PolicyInformationPointSpecification("abc.def", true,
                PolicyInformationPointSpecification.HAS_VARIABLE_NUMBER_OF_ARGUMENTS, true, List.of(), List.of());
        assertThat(withVarArgs.hasVariableNumberOfArguments()).isTrue();
        var notWithVarArgs = new PolicyInformationPointSpecification("abc.def", true, 0, true, List.of(), List.of());
        assertThat(notWithVarArgs.hasVariableNumberOfArguments()).isFalse();
    }

}
