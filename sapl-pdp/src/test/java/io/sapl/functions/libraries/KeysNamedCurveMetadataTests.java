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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPublicKeySpec;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.sapl.api.functions.FunctionInvocation;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.functions.DefaultFunctionBroker;
import io.sapl.functions.libraries.crypto.PemUtils;
import lombok.val;

@DisplayName("Named EC curve metadata uses the supported public format")
class KeysNamedCurveMetadataTests {

    @ParameterizedTest(name = "{0}")
    @MethodSource("supportedCurves")
    void whenPublicParametersUseSupportedCurveThenMetadataMatches(String curve, String jwkName, int bits)
            throws Exception {
        val pem    = publicGenerator(curve);
        val broker = broker();

        assertThat(call(broker, "publicKeyFromPem", pem)).isInstanceOfSatisfying(ObjectValue.class,
                value -> assertThat(value).containsEntry("algorithm", Value.of("EC"))
                        .containsEntry("format", Value.of("X.509")).containsEntry("size", Value.of(bits))
                        .containsEntry("curve", Value.of(curve)));
        assertThat(List.of(call(broker, "curveFromKey", pem), call(broker, "algorithmFromKey", pem),
                call(broker, "sizeFromKey", pem))).containsExactly(Value.of(curve), Value.of("EC"), Value.of(bits));
        assertThat(call(broker, "jwkFromPublicKey", pem)).isInstanceOfSatisfying(ObjectValue.class,
                value -> assertThat(value).containsEntry("kty", Value.of("EC")).containsEntry("crv",
                        Value.of(jwkName)));
    }

    static Stream<Arguments> supportedCurves() {
        return Stream.of(arguments("secp256r1", "P-256", 256), arguments("secp384r1", "P-384", 384),
                arguments("secp521r1", "P-521", 521));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "publicKeyFromPem", "algorithmFromKey", "sizeFromKey", "curveFromKey",
            "jwkFromPublicKey" })
    void whenPublicParametersUseAnotherNamedCurveThenMetadataReturnsAnError(String function) throws Exception {
        val pem    = publicGenerator("secp256k1");
        val broker = broker();

        assertThat(call(broker, function, pem)).isInstanceOf(ErrorValue.class);
    }

    private static DefaultFunctionBroker broker() {
        val broker = new DefaultFunctionBroker();
        broker.load(new KeysFunctionLibrary());
        return broker;
    }

    private static Value call(DefaultFunctionBroker broker, String function, String pem) {
        return broker.evaluateFunction(new FunctionInvocation("keys." + function, List.of(Value.of(pem))));
    }

    private static String publicGenerator(String curve) throws Exception {
        val parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec(curve));
        val spec = parameters.getParameterSpec(ECParameterSpec.class);
        val key  = KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(spec.getGenerator(), spec));
        return PemUtils.encodePublicKeyPem(key.getEncoded());
    }
}
