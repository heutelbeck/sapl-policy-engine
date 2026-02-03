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
package io.sapl.attributes;

import io.sapl.api.attributes.AttributeFinder;
import io.sapl.api.attributes.AttributeFinderSpecification;
import io.sapl.api.model.Value;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AttributeFinderSpecification")
class AttributeFinderSpecificationTests {

    private static final AttributeFinder MOCK_FINDER = inv -> Flux.just(Value.UNDEFINED);

    @Test
    void whenConstructingWithBadParametersThenThrows() {
        val validName = "a";
        val emptyList = List.<Class<? extends Value>>of();
        assertThatThrownBy(() -> new AttributeFinderSpecification(null, validName, true, emptyList, null, MOCK_FINDER))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AttributeFinderSpecification(validName, null, true, emptyList, null, MOCK_FINDER))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void whenVarArgsCheckedThenVarArgsCorrectlyDetected() {
        var withVarArgs    = new AttributeFinderSpecification("abc", "def", true, List.of(), Value.class, MOCK_FINDER);
        var notWithVarArgs = new AttributeFinderSpecification("abc", "def", true, List.of(), null, MOCK_FINDER);
        assertThat(withVarArgs.hasVariableNumberOfArguments()).isTrue();
        assertThat(notWithVarArgs.hasVariableNumberOfArguments()).isFalse();
    }

}
