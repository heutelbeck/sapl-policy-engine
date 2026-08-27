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
package io.sapl.attributeapi.attributes.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.sapl.api.model.Value;
import io.sapl.attributeapi.attributes.backend.AttributeStore;
import io.sapl.attributeapi.auth.AttributeApiSecurityProperties;

public class AttributeApiServiceTests {
    private AttributeStore                 store;
    private AttributeApiSecurityProperties properties;

    @BeforeEach
    void setUp() {
        store      = mock(AttributeStore.class);
        properties = new AttributeApiSecurityProperties();
    }

    @Test
    @DisplayName("A request with more requests than a custom maximum of 25 is rejected by the API server")
    void whenArgumentsLimitExceededACustomMaximumThenRequestIsRejected() {
        properties.setMaxArguments(25);
        var service = new AttributeApiService(store, properties);
        var args    = generateXArguments(26);

        assertThatThrownBy(() -> service.get("entity", "sapl.attribute", args, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("A request with more requests than the default maximum of 50 is rejected by the API server")
    void whenArgumentsLimitExceededTheDefaultMaximumThenRequestIsRejected() {
        var service = new AttributeApiService(store, properties);
        var args    = generateXArguments(51);

        assertThatThrownBy(() -> service.get("entity", "sapl.attribute", args, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("A request with exactly 50 arguments and a default maximum of 50 is accepted by the API server")
    void whenArgumentsLimitIsNotReachedThenTheRequestIsAccepted() {
        // Stub for the store because a valid request reached the store that is not needed here
        when(store.get(any(), any())).thenReturn(Value.UNDEFINED);

        var service = new AttributeApiService(store, properties);
        var args    = generateXArguments(50);

        // Store reached because the store throws the NoSuchElementException and the argument-limit check is passed
        assertThatThrownBy(() -> service.get("entity", "sapl.attribute", args, null))
                .isInstanceOf(NoSuchElementException.class);
    }

    // Return a list of arguments like ["1","2",...,"n"]
    private static List<String> generateXArguments(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(String::valueOf).toList();
    }
}
