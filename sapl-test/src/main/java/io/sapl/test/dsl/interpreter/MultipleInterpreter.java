/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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

package io.sapl.test.dsl.interpreter;

import io.sapl.test.SaplTestException;
import io.sapl.test.grammar.sapltest.Multiple;

class MultipleInterpreter {
    int getAmountFromMultiple(final Multiple multiple) {
        int intValue;

        try {
            final var amount = multiple.getAmount();

            if (amount == null) {
                throw new SaplTestException("Amount is null");
            }

            intValue = amount.intValueExact();
        } catch (ArithmeticException e) {
            throw new SaplTestException("Amount has invalid format", e);
        }

        if (intValue < 2) {
            throw new SaplTestException("Amount needs to be larger than 1");
        }

        return intValue;
    }
}
