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
package io.sapl.test.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.pdp.config.PolicyDecisionPointConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.SignalType;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

import static org.assertj.core.api.Assertions.*;

class ClasspathVariablesAndCombinatorSourceTests {

    @Test
    void doTest() {
        final var configProvider = new ClasspathVariablesAndCombinatorSource("policiesIT", new ObjectMapper(), null,
                null);
        assertThat(configProvider.getCombiningAlgorithm().blockFirst())
                .contains(PolicyDocumentCombiningAlgorithm.DENY_UNLESS_PERMIT);
        assertThat(configProvider.getVariables().log(null, Level.INFO, SignalType.ON_NEXT).blockFirst().get().keySet())
                .isEmpty();
        configProvider.destroy();
    }

    @Test
    void test_nullPath() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ClasspathVariablesAndCombinatorSource(null, new ObjectMapper(), null, null));
    }

    @Test
    void test_nullObjectMapper() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ClasspathVariablesAndCombinatorSource("", null, null, null));
    }

    @Test
    void test_IOException() throws IOException {
        final var mapper = Mockito.mock(ObjectMapper.class);
        Mockito.when(mapper.readValue((File) Mockito.any(), Mockito.<Class<PolicyDecisionPointConfiguration>>any()))
                .thenThrow(new IOException());
        assertThatExceptionOfType(IOException.class)
                .isThrownBy(() -> new ClasspathVariablesAndCombinatorSource("policiesIT", mapper, null, null));
    }

}
