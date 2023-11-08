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
package io.sapl.pdp;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.interpreter.InitializationException;

class PolicyDecisionPointFactoryTest {

    @Test
    void test_factory_methods() throws InitializationException {
        assertThat(PolicyDecisionPointFactory.filesystemPolicyDecisionPoint(), notNullValue());
        assertThat(PolicyDecisionPointFactory.filesystemPolicyDecisionPoint("src/main/resources/policies"),
                notNullValue());
        assertThat(PolicyDecisionPointFactory.filesystemPolicyDecisionPoint("src/main/resources/policies",
                Collections.singletonList(new TestPIP()), () -> List.of(), () -> List.of()), notNullValue());

        assertThat(PolicyDecisionPointFactory.filesystemPolicyDecisionPoint(List.of(new TestPIP()), () -> List.of(),
                () -> List.of(FilterFunctionLibrary.class)), notNullValue());

        assertThat(PolicyDecisionPointFactory.resourcesPolicyDecisionPoint(), notNullValue());
        assertThat(PolicyDecisionPointFactory.resourcesPolicyDecisionPoint(List.of(new TestPIP()), () -> List.of(),
                () -> List.of()), notNullValue());
    }

}
