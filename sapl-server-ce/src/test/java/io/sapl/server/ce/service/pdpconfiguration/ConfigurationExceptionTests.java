/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.server.ce.service.pdpconfiguration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ConfigurationExceptionTests {
    @Test
    public void nullResistance() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            new DuplicatedVariableNameException(null);
        });

        Assertions.assertThrows(NullPointerException.class, () -> {
            new InvalidJsonException(null);
        });
        Assertions.assertThrows(NullPointerException.class, () -> {
            new InvalidJsonException(null, new Exception());
        });

        Assertions.assertThrows(NullPointerException.class, () -> {
            new InvalidVariableNameException(null);
        });
    }
}
