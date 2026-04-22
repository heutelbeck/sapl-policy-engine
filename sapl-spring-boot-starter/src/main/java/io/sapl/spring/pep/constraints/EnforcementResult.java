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

import io.sapl.spring.util.Maybe;

/**
 * Outcome of executing the enforcement plan for one signal.
 *
 * @param value the (possibly transformed) value carried by the signal, or
 * {@link Maybe.Absent} for
 * self-contained signals
 * @param failureState {@code true} once at least one obligation handler has
 * failed during execution; once
 * {@code true} it remains {@code true} for the remainder of the enforcement run
 */
public record EnforcementResult<T>(Maybe<T> value, boolean failureState) {}
