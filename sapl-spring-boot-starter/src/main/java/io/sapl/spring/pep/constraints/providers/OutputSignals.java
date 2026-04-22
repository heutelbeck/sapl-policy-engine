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
package io.sapl.spring.pep.constraints.providers;

import java.util.Optional;
import java.util.Set;

import io.sapl.spring.pep.constraints.Signal;
import io.sapl.spring.pep.constraints.SignalType;
import io.sapl.spring.pep.constraints.SignalType.ValueSignalType;
import lombok.experimental.UtilityClass;

/**
 * Lookup helpers for finding specific signal types in a PEP's set of supported
 * signals. Reused by content
 * providers that bind their handler to the {@link Signal.OutputSignal} the PEP
 * fires.
 */
@UtilityClass
class OutputSignals {

    /**
     * Returns the first {@link ValueSignalType} matching
     * {@link Signal.OutputSignal} in {@code supported},
     * or {@link Optional#empty()} if the PEP does not fire an OutputSignal.
     */
    static Optional<ValueSignalType<?>> findIn(Set<SignalType> supported) {
        for (var signal : supported) {
            if (signal instanceof ValueSignalType<?> v && Signal.OutputSignal.class.equals(v.type())) {
                return Optional.of(v);
            }
        }
        return Optional.empty();
    }
}
