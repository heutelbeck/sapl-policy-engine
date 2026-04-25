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

/**
 * Pairs a {@link ConstraintHandler} with the {@link SignalType} it applies to
 * and a sort priority.
 * </p>
 *
 * @param handler the constraint handler
 * @param signalType the signal at which {@code handler} applies
 * @param priority sort key; lower values sort first
 */
public record ScopedConstraintHandler(ConstraintHandler<?> handler, SignalType signalType, int priority) {}
