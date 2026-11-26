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

import java.util.List;
import java.util.stream.Stream;
import io.sapl.api.functions.FunctionInvocation;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.functions.DefaultFunctionBroker;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Public standard coordinates in JWK output")
class KeysPublicCoordinateFormattingTests {
    @MethodSource("standardGenerators")
    @ParameterizedTest(name = "{0}")
    @DisplayName("Coordinates retain their full curve field width")
    void whenPublicGeneratorIsWrittenAsJwkThenCoordinatesRetainFieldWidth(String curve, String pem, String x,
            String y) {
        val broker = new DefaultFunctionBroker();
        broker.load(new KeysFunctionLibrary());
        val invocation = new FunctionInvocation("keys.jwkFromPublicKey", List.of(Value.of(pem)));

        val actual = broker.evaluateFunction(invocation);

        assertThat(actual).isInstanceOfSatisfying(ObjectValue.class,
                jwk -> assertThat(jwk).containsEntry("kty", Value.of("EC")).containsEntry("crv", Value.of(curve))
                        .containsEntry("x", Value.of(x)).containsEntry("y", Value.of(y)).hasSize(4));
    }

    static Stream<Arguments> standardGenerators() {
        return Stream.of(
                arguments("P-256",
                        """
                                -----BEGIN PUBLIC KEY-----
                                MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEaxfR8uEsQkf4vOblY6RA8ncDfYEt6zOg9KE5RdiYwpZP40Li/hp/m47n60p8D54WK84zV2sxXs7LtkBoN79R9Q==
                                -----END PUBLIC KEY-----
                                """,
                        "axfR8uEsQkf4vOblY6RA8ncDfYEt6zOg9KE5RdiYwpY", "T-NC4v4af5uO5-tKfA-eFivOM1drMV7Oy7ZAaDe_UfU"),
                arguments("P-384",
                        """
                                -----BEGIN PUBLIC KEY-----
                                MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEqofKIr6LBTeOscce8yCtdG4dO2KLp5uYWfdB4IJUKjhVAvJdv1UpbDpUXjhydgq3NhfeSpYmLG9dnpi/kpLcKfj0Hb0omhR86doxE7XwuMAKYLHOHX6BnXpDHXyQ6g5f
                                -----END PUBLIC KEY-----
                                """,
                        "qofKIr6LBTeOscce8yCtdG4dO2KLp5uYWfdB4IJUKjhVAvJdv1UpbDpUXjhydgq3",
                        "NhfeSpYmLG9dnpi_kpLcKfj0Hb0omhR86doxE7XwuMAKYLHOHX6BnXpDHXyQ6g5f"),
                arguments("P-521",
                        """
                                -----BEGIN PUBLIC KEY-----
                                MIGbMBAGByqGSM49AgEGBSuBBAAjA4GGAAQAxoWOBrcEBOnNnj7LZiOVtEKcZIE5BT+1Ifgor2BrTT26oUted+/nWSj+HcEnov+o3jNIs8GFakKb+X5+McLlvWYBGDkpaniaO8AEXIpftCx9G9mY9URJV5tEaBevvRcnPmYsl+5ymV70JkDFULkBP60HYTU8cIaicsJAiL6Udp/RZlA=
                                -----END PUBLIC KEY-----
                                """,
                        "AMaFjga3BATpzZ4-y2YjlbRCnGSBOQU_tSH4KK9ga009uqFLXnfv51ko_h3BJ6L_qN4zSLPBhWpCm_l-fjHC5b1m",
                        "ARg5KWp4mjvABFyKX7QsfRvZmPVESVebRGgXr70XJz5mLJfucple9CZAxVC5AT-tB2E1PHCGonLCQIi-lHaf0WZQ"));
    }
}
