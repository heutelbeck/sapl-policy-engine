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

class ReflectionHelper {
    Object constructInstanceOfClass(final String className) {
        if (className == null || className.isEmpty()) {
            throw new SaplTestException("null or empty className");
        }
        try {
            final var clazz = Class.forName(className);

            final var constructor = clazz.getConstructor();
            return constructor.newInstance();

        } catch (Exception e) {
            throw new SaplTestException("Could not construct instance of '%s' class".formatted(className));
        }
    }
}
