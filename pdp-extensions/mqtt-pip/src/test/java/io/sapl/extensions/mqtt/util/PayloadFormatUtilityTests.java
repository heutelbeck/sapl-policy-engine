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
package io.sapl.extensions.mqtt.util;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.Value;
import lombok.val;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PayloadFormatUtility")
class PayloadFormatUtilityTests {

    @Nested
    @DisplayName("convertBytesToArrayValue")
    class ConvertBytesToArrayValue {

        @Test
        @DisplayName("high bytes (>= 0x80) are represented as unsigned integers 0..255")
        void whenPayloadHasHighBytesThenElementsAreUnsigned() {
            val result = PayloadFormatUtility
                    .convertBytesToArrayValue(new byte[] { (byte) 0xFF, (byte) 0xFE, (byte) 0x80, 0x00, 0x7F });

            assertThat(result).isInstanceOf(ArrayValue.class).asInstanceOf(InstanceOfAssertFactories.list(Value.class))
                    .extracting(element -> ((NumberValue) element).value()).containsExactly(BigDecimal.valueOf(255),
                            BigDecimal.valueOf(254), BigDecimal.valueOf(128), BigDecimal.valueOf(0),
                            BigDecimal.valueOf(127));
        }
    }
}
