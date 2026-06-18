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

import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.attributes.http.BlockingWebClient;
import io.sapl.api.stream.Streams;
import io.sapl.api.test.stream.StreamAssertions;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static io.sapl.api.model.ValueJsonMarshaller.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Traccar inline configuration form")
class TraccarInlineConfigTests {

    private static final ObjectValue BASE_CONFIG = (ObjectValue) json("""
            {
                "baseUrl": "http://localhost:8082"
            }
            """);

    @Mock
    private BlockingWebClient mockWebClient;

    private static ObjectValue secretsWithToken(String token) {
        return ObjectValue.builder().put("traccar", ObjectValue.builder().put("token", Value.of(token)).build())
                .build();
    }

    private static AttributeAccessContext ctxWithSecrets(ObjectValue pdpSecrets) {
        return new AttributeAccessContext(Value.EMPTY_OBJECT, pdpSecrets, Value.EMPTY_OBJECT);
    }

    @Test
    @DisplayName("when inline config form is used then credentials come from pdpSecrets")
    void whenInlineConfigFormUsedThenCredentialsComeFromPdpSecrets() {
        when(mockWebClient.httpRequest(any(), any())).thenAnswer(invocation -> Streams.just(json("{}")));
        val pip = new TraccarPolicyInformationPoint(mockWebClient);
        val ctx = ctxWithSecrets(secretsWithToken("my-api-token"));

        try (val stream = pip.server(ctx, BASE_CONFIG)) {
            StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(v).isNotInstanceOf(ErrorValue.class));
        }

        verify(mockWebClient).httpRequest(any(), argThat(settings -> {
            val urlParams = settings.get(BlockingWebClient.URL_PARAMS);
            return urlParams instanceof ObjectValue paramsObj && paramsObj.containsKey("token");
        }));
    }

    @Test
    @DisplayName("when device entity id contains URL-structure characters then it is percent-encoded into the path")
    void whenDeviceEntityIdContainsUrlStructureCharactersThenItIsPercentEncoded() {
        when(mockWebClient.httpRequest(any(), any())).thenAnswer(invocation -> Streams.just(json("{}")));
        val pip         = new TraccarPolicyInformationPoint(mockWebClient);
        val secrets     = secretsWithToken("my-api-token");
        val maliciousId = Value.of("1/../../api/server?admin=1");

        try (val stream = pip.device(maliciousId, BASE_CONFIG, secrets)) {
            StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(v).isNotInstanceOf(ErrorValue.class));
        }

        verify(mockWebClient).httpRequest(any(), argThat(settings -> {
            val path = settings.get(BlockingWebClient.PATH);
            return path instanceof TextValue(var pathString)
                    && "/api/devices/1%2F..%2F..%2Fapi%2Fserver%3Fadmin%3D1".equals(pathString);
        }));
    }

    @Test
    @DisplayName("when geofence entity id contains URL-structure characters then it is percent-encoded into the path")
    void whenGeofenceEntityIdContainsUrlStructureCharactersThenItIsPercentEncoded() {
        when(mockWebClient.httpRequest(any(), any())).thenAnswer(invocation -> Streams.just(json("{}")));
        val pip         = new TraccarPolicyInformationPoint(mockWebClient);
        val secrets     = secretsWithToken("my-api-token");
        val maliciousId = Value.of("1#fragment");

        try (val stream = pip.traccarGeofence(maliciousId, BASE_CONFIG, secrets)) {
            StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(v).isNotInstanceOf(ErrorValue.class));
        }

        verify(mockWebClient).httpRequest(any(), argThat(settings -> {
            val path = settings.get(BlockingWebClient.PATH);
            return path instanceof TextValue(var pathString) && "/api/geofences/1%23fragment".equals(pathString);
        }));
    }
}
