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
package io.sapl.functions.libraries;

import io.sapl.api.functions.FunctionInvocation;
import io.sapl.api.model.Value;
import io.sapl.functions.DefaultFunctionBroker;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("X.509 names")
class X509NameFunctionsTests {

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = { "extractCommonName | second",
            "extractSubjectDn | CN=second,OU=unit+CN=first,O=Multi",
            "extractIssuerDn | CN=second,OU=unit+CN=first,O=Multi" })
    @DisplayName("The most specific common name is selected without changing the DN")
    void whenSubjectContainsMultipleCommonNamesThenMostSpecificIsReturned(String function, String expected)
            throws IOException {
        try (val input = getClass().getResourceAsStream("/x509/multiple-common-names.pem")) {
            assertThat(input).isNotNull();
            val certificate = new String(input.readAllBytes(), StandardCharsets.US_ASCII);
            val broker      = new DefaultFunctionBroker();
            broker.load(new X509FunctionLibrary());

            val actual = broker
                    .evaluateFunction(new FunctionInvocation("x509." + function, List.of(Value.of(certificate))));

            assertThat(actual).isEqualTo(Value.of(expected));
        }
    }
}
