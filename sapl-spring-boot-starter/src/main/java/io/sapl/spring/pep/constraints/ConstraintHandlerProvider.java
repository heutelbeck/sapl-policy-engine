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

import java.util.Optional;
import java.util.Set;

import io.sapl.api.model.Value;

/**
 * Implementation of Algorithm 1 of the enforcement framework: maps a constraint
 * to a scoped handler.
 * <p>
 * Returns {@link Optional#empty()} when this provider does not implement the
 * constraint. Otherwise, returns the
 * triple {@code (handler, signalType, priority)} that the planner will
 * schedule. Implementations may use
 * {@code supportedSignals} to bind their handler to the {@link SignalType} the
 * deployed PEP actually fires
 * (e.g., picking the {@link Signal.OutputSignal} type with its concrete
 * {@code valueType}).
 */
public interface ConstraintHandlerProvider {
    Optional<ScopedConstraintHandler> getConstraintHandler(Value constraint, Set<SignalType> supportedSignals);
}
