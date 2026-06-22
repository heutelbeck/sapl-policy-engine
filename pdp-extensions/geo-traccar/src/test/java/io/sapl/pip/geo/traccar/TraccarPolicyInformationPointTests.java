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
package io.sapl.pip.geo.traccar;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.stream.Streams;
import io.sapl.api.test.stream.StreamAssertions;
import io.sapl.attributes.http.BlockingWebClient;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static io.sapl.api.model.ValueJsonMarshaller.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Traccar policy information point")
class TraccarPolicyInformationPointTests {

    private static final ObjectValue BASE_CONFIG = (ObjectValue) json("""
            {
                "baseUrl": "http://localhost:8082",
                "allowInsecureHttp": true
            }
            """);

    @Mock
    private BlockingWebClient mockWebClient;

    private static ObjectValue secretsWithToken(String token) {
        return ObjectValue.builder().put("traccar", ObjectValue.builder().put("token", Value.of(token)).build())
                .build();
    }

    @Test
    @DisplayName("when the position request fails upstream then the original error is propagated unchanged")
    void whenPositionRequestFailsUpstreamThenOriginalErrorIsPropagated() {
        val upstreamError = Value.error("Connection refused: Traccar server unreachable.");
        when(mockWebClient.httpRequest(any(), any())).thenReturn(Streams.just(upstreamError));
        val pip     = new TraccarPolicyInformationPoint(mockWebClient);
        val secrets = secretsWithToken("my-api-token");

        try (val stream = pip.traccarPosition((TextValue) Value.of("12345"), BASE_CONFIG, secrets)) {
            StreamAssertions.assertThat(stream)
                    .awaitsNext(value -> assertThat(value).asInstanceOf(type(ErrorValue.class))
                            .extracting(ErrorValue::message)
                            .isEqualTo("Connection refused: Traccar server unreachable."));
        }
    }
}
