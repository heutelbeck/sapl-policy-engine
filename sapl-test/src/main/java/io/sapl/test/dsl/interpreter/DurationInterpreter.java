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

import java.time.DateTimeException;
import java.time.Duration;

import io.sapl.test.SaplTestException;

class DurationInterpreter {
    Duration getJavaDurationFromDuration(final io.sapl.test.grammar.sapltest.Duration duration) {
        if (duration == null) {
            throw new SaplTestException("The passed Duration is null");
        }
        try {
            final var parsedDuration = java.time.Duration.parse(duration.getDuration());

            if (parsedDuration.isZero() || parsedDuration.isNegative()) {
                throw new SaplTestException("The passed Duration needs to be larger than 0");
            }
            return parsedDuration;

        } catch (DateTimeException | ArithmeticException e) {
            throw new SaplTestException("The provided Duration has an invalid format", e);
        }
    }
}
