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
package io.sapl.attributes.broker.pip;

import io.sapl.api.stream.Stream;
import lombok.experimental.UtilityClass;
import lombok.val;
import java.util.function.Supplier;

/** Identifies source acquisition and release on a thread, including reentry through another broker. */
@UtilityClass
class AttributeSourceCall {
    private static final ThreadLocal<Boolean> IN_SOURCE = new ThreadLocal<>();

    static boolean active() {
        return IN_SOURCE.get() != null;
    }

    static <T> T invoke(Supplier<T> operation) {
        val prior = IN_SOURCE.get();
        IN_SOURCE.set(Boolean.TRUE);
        try {
            return operation.get();
        } finally {
            if (prior == null) {
                IN_SOURCE.remove();
            } else {
                IN_SOURCE.set(prior);
            }
        }
    }

    static void close(Stream<?> stream) {
        invoke(() -> {
            stream.close();
            return null;
        });
    }
}
