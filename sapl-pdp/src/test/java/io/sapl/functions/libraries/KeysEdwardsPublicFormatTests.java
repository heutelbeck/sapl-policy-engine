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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("Edwards public key formats")
class KeysEdwardsPublicFormatTests {

    private static Stream<Arguments> publicKeys() {
        return Stream.of(arguments("Ed25519", "ed25519.pem", "UlanPqrsekLnXd9n6sNXtBiJ39EiSrNwfmKeNbXxxIs"), arguments(
                "Ed448", "ed448.pem", "weJUDtEeYQKlyBKnorpHDxc6DfVlil4tkn-GkQk91GRDcfD5VwT622sHJm2PCzcwdvtwe9c3icwA"));
    }

    @ParameterizedTest(name = "{0} keeps its curve and public bytes in both formats")
    @MethodSource("publicKeys")
    void whenEdwardsPublicKeyIsConvertedThenJwkAndPemPreserveIt(String curve, String resource, String point)
            throws IOException {
        String pem;
        try (val in = Objects.requireNonNull(getClass().getResourceAsStream("/keys/edwards/" + resource))) {
            pem = new String(in.readAllBytes(), StandardCharsets.US_ASCII);
        }
        val broker = new DefaultFunctionBroker();
        broker.load(new KeysFunctionLibrary());
        val expected = Value.ofObject(Map.of("kty", Value.of("OKP"), "crv", Value.of(curve), "x", Value.of(point)));
        val written  = broker.evaluateFunction(new FunctionInvocation("keys.jwkFromPublicKey", List.of(Value.of(pem))));
        val read     = broker.evaluateFunction(new FunctionInvocation("keys.publicKeyFromJwk", List.of(expected)));

        assertThat(List.of(written, read)).isEqualTo(List.of(expected, Value.of(pem)));
    }
}
