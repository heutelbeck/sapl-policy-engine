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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class SignatureContractTests {

    private static final String RSA_PUBLIC_KEY = """
            -----BEGIN PUBLIC KEY-----
            MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAntOEZwlTvjtVJBCumeQs
            eZVDJ47RWp8NnRZCei0SSC0mMht6w+yPVSjOIyBbMJ24nYO9IegqUu7iqSiKSvHI
            Ry22OTW5n/v6EjaFlIlVtBuEEROb5oXtPWn8dKA7x6qjnkXptMtYX4Mm7Kdtll2N
            xh/J5k5pcgWtTe8kmPCD/0fc7BZOFnMfdT5sWwTLwl5eP1wlHIvxrUEMnuW2SpJt
            jvqZgUnSV3+4+Tp+MtHQe2Lh73h2ZTNRjPsMo/VnwR57Y8kyXBnClskZguBGOr1l
            OzY0FdI/FHkQx6gxV7POTOsdGZw6JT9+ugph9Pa7Ol+8nDz7lg/6BhcT9db0m1Vb
            hwIDAQAB
            -----END PUBLIC KEY-----
            """;

    private static final String RSA_SIGNATURE = "D0PZoO7+p7GnQqjlpOnOYvVFYTyFLaWlW32KHpzRkvKQdSEKuoWo+L9HpRWW2oD4"
            + "Qc/xIxXy0scBq4QK35+v8WrgLwKbYqIfjNDxOdImGsUcM6ZdipuZ1m1cgLsD4Tww"
            + "ruwPznfmAYFlQ8armafsWZo3WPSP9VjFXYXH3yNif8+RIEWc/liILWwQ33r0Sx/G"
            + "ogaxTjMUU4+5FKua236B/VoVuX7v09xdy26vZT8MP77RcmZtU6VMZcLAOaow8q7o"
            + "7mEvGwsiedxxZNAbZkVwWqwbqqxm0HLlnOfFOYSA1a7DoVn6b+emSeNUvRwaf9KG" + "1q2NQu4GYFce8IXqWKvOjA==";

    private static final String ED25519_PUBLIC_KEY = """
            -----BEGIN PUBLIC KEY-----
            MCowBQYDK2VwAyEAUlanPqrsekLnXd9n6sNXtBiJ39EiSrNwfmKeNbXxxIs=
            -----END PUBLIC KEY-----
            """;

    private static final String ED25519_SIGNATURE = "5qLJ67fCbY0QmyS10XjVRTFjMI8g3KdZvUp0m0SyUPUYsmXLWe1G0PB3bSOgw1Ye"
            + "qgqChvo3HMONV4VOecp+BQ==";

    @ParameterizedTest(name = "{0} accepts standard and URL-safe Base64 with optional padding")
    @MethodSource("publishedSignatures")
    void whenPublishedSignaturesUseEitherBase64AlphabetThenTheyVerify(String function, String message, String signature,
            String publicKey) {
        val broker = new DefaultFunctionBroker();
        broker.load(new SignatureFunctionLibrary());
        val urlSafe = signature.replace('+', '-').replace('/', '_');
        for (String spelling : List.of(signature, signature.replaceAll("=+$", ""), urlSafe,
                urlSafe.replaceAll("=+$", ""))) {
            val arguments = List.<Value>of(Value.of(message), Value.of(spelling), Value.of(publicKey));
            assertThat(broker.evaluateFunction(new FunctionInvocation(function, arguments))).isEqualTo(Value.TRUE);
        }
    }

    static Stream<Arguments> publishedSignatures() {
        return Stream.of(arguments("signature.isValidRsaSha256", "hello", RSA_SIGNATURE, RSA_PUBLIC_KEY),
                arguments("signature.isValidEd25519", "hello", ED25519_SIGNATURE, ED25519_PUBLIC_KEY));
    }
}
