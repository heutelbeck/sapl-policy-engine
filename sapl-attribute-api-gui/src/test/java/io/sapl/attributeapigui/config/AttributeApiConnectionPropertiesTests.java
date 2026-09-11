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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AttributeApiConnectionPropertiesTests {
    AttributeApiConnectionProperties properties = new AttributeApiConnectionProperties();

    @Test
    @DisplayName("When a URL is blank then throw an exception that the base url is not set.")
    void whenURLIsBlankThenThrowAnException() {
        properties.setBaseUrl("");
        assertThatThrownBy(() -> properties.afterPropertiesSet()).isInstanceOf(IllegalStateException.class).hasMessage(
                "The base url is not set. Please set io.sapl.attribute-api-gui.connection.base-url via settings");
    }

    @Test
    @DisplayName("When the URL is malformed then throw an exception thate url is malformed and not a valid url")
    void whenURLIsMalformedThenThrowAnException() {
        properties.setBaseUrl("test___");
        assertThatThrownBy(() -> properties.afterPropertiesSet()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("The given url is not a valid url");
    }
}
