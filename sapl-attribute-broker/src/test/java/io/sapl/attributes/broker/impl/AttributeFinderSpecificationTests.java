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
package io.sapl.attributes.broker.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.sapl.attributes.broker.api.AttributeFinderSpecification;

class AttributeFinderSpecificationTests {

    @Test
    void whenConstructionOfPolicyInformationPointSpecificationHasBadParametersThenThrowElseDoNotThrow() {
        assertThatThrownBy(() -> new AttributeFinderSpecification(null, true, 0, true, e -> {}, List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void whenVarArgsCheckedThenVarArgsCorrectlyDetected() {
        var withVarArgs = new AttributeFinderSpecification("abc.def", true,
                AttributeFinderSpecification.HAS_VARIABLE_NUMBER_OF_ARGUMENTS, true, e -> {}, List.of());
        assertThat(withVarArgs.hasVariableNumberOfArguments()).isTrue();
        var notWithVarArgs = new AttributeFinderSpecification("abc.def", true, 0, true, e -> {}, List.of());
        assertThat(notWithVarArgs.hasVariableNumberOfArguments()).isFalse();
    }

}
