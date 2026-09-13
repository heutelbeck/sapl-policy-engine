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
package io.sapl.attributeapigui.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AttributeApiConnectionPropertiesTests {
    AttributeApiConnectionProperties properties = new AttributeApiConnectionProperties();

    @Test
    @DisplayName("When a URL is blank then throw an exception that the base url is not set.")
    void whenURLIsBlankThenThrowAnException() {
        var entry = new AttributeApiConnectionProperties.ConnectionEntry();
        entry.setBaseUrl("");
        properties.setConnections(List.of(entry));

        assertThatThrownBy(() -> properties.afterPropertiesSet()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("The base url is not set");
    }

    @Test
    @DisplayName("When the URL is malformed then throw an exception thate url is malformed and not a valid url")
    void whenURLIsMalformedThenThrowAnException() {
        var entry = new AttributeApiConnectionProperties.ConnectionEntry();
        entry.setBaseUrl("test___");
        properties.setConnections(List.of(entry));

        assertThatThrownBy(() -> properties.afterPropertiesSet()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("The given url is not a valid url");
    }

    @Test
    @DisplayName("When the connections block is absent then no exception is thrown and the list is empty")
    void whenConnectionsBlockIsAbsentThenNoExceptionIsThrown() {
        assertThatCode(() -> properties.afterPropertiesSet()).doesNotThrowAnyException();
        assertThat(properties.getConnections()).isEmpty();
    }

    @Test
    @DisplayName("When the connections block is present but empty then throw an exception")
    void whenConnectionsBlockIsEmptyThenThrowAnException() {
        properties.setConnections(List.of());

        assertThatThrownBy(() -> properties.afterPropertiesSet()).isInstanceOf(IllegalStateException.class).hasMessage(
                "At least one connection must be defined within this block or remove it to start without configure connections.");
    }
}
