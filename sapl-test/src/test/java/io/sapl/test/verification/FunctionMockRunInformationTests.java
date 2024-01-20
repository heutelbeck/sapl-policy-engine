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
package io.sapl.test.verification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.test.mocking.MockCall;

class FunctionMockRunInformationTests {

    @Test
    void test_initialization() {
        var fullName = "foo";
        var mock     = new MockRunInformation(fullName);

        assertThat(mock.getFullName()).isEqualTo(fullName);
        assertThat(mock.getTimesCalled()).isZero();
        assertThat(mock.getCalls()).isNotNull();
    }

    @Test
    void test_increase() {
        var fullName = "foo";
        var call     = new MockCall(Val.of("foo"));
        var mock     = new MockRunInformation(fullName);

        mock.saveCall(call);

        assertThat(mock.getTimesCalled()).isEqualTo(1);
        assertThat(mock.getCalls().get(0).isUsed()).isFalse();
        assertThat(mock.getCalls().get(0).getCall().getNumberOfArguments()).isEqualTo(1);
        assertThat(mock.getCalls().get(0).getCall().getArgument(0)).isEqualTo(Val.of("foo"));
    }

}
