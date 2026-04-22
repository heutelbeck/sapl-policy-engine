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
package io.sapl.spring.pep.constraints;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class EnforcementPlan {
    private final Map<SignalType, List<EnforcementPlanEntry<?>>> stepsBySignalType = new HashMap<>();

    public void add(SignalType signalType, EnforcementPlanEntry<?> enforcementPlanEntry) {
        stepsBySignalType.computeIfAbsent(signalType, k -> List.of()).add(enforcementPlanEntry);
    }

    public record EnforcementResult<T>(Maybe<T> value, boolean failureState) {}

    public EnforcementResult<?> executePlan(Signal signal, boolean failureState) {
        Object value = null;
        if (signal instanceof Signal.ValueSignal<?> valueSignal) {
            value = valueSignal.value();
        }
        if (signal instanceof Signal.ValueSignal<?> valueSignal) {
            return new EnforcementResult<>(Maybe.of(value), failureState);
        } else {
            return new EnforcementResult<>(Maybe.absent(), failureState);
        }

    }
}
