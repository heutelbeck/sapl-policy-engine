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
package io.sapl.pdp.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;

import io.sapl.grammar.sapl.CombiningAlgorithm;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;

class PDPConfigurationTests {

    @Test
    void testIsValid() {
        assertThat(new PDPConfiguration(null, mock(FunctionContext.class), new HashMap<>(),
                mock(CombiningAlgorithm.class), UnaryOperator.identity(), UnaryOperator.identity()).isValid(),
                is(false));
        assertThat(
                new PDPConfiguration(mock(AttributeContext.class), null, new HashMap<>(),
                        mock(CombiningAlgorithm.class), UnaryOperator.identity(), UnaryOperator.identity()).isValid(),
                is(false));
        assertThat(
                new PDPConfiguration(mock(AttributeContext.class), mock(FunctionContext.class), null,
                        mock(CombiningAlgorithm.class), UnaryOperator.identity(), UnaryOperator.identity()).isValid(),
                is(false));
        assertThat(new PDPConfiguration(mock(AttributeContext.class), mock(FunctionContext.class), new HashMap<>(),
                null, UnaryOperator.identity(), UnaryOperator.identity()).isValid(), is(false));
        assertThat(
                new PDPConfiguration(mock(AttributeContext.class), mock(FunctionContext.class), new HashMap<>(),
                        mock(CombiningAlgorithm.class), UnaryOperator.identity(), UnaryOperator.identity()).isValid(),
                is(true));
        assertThat(new PDPConfiguration(mock(AttributeContext.class), mock(FunctionContext.class), new HashMap<>(),
                mock(CombiningAlgorithm.class), UnaryOperator.identity(), null).isValid(), is(false));
        assertThat(new PDPConfiguration(mock(AttributeContext.class), mock(FunctionContext.class), new HashMap<>(),
                mock(CombiningAlgorithm.class), null, UnaryOperator.identity()).isValid(), is(false));
    }

}
