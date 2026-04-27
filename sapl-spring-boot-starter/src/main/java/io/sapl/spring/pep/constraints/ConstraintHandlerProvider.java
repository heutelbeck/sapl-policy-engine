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

import java.util.List;
import java.util.Set;

import io.sapl.api.model.Value;

/**
 * Translates one constraint into the constraint handlers that enforce it.
 * <p>
 * A provider returns an empty list when it does not recognise the
 * constraint. Otherwise it returns one or more
 * {@link ScopedConstraintHandler} entries. Each entry pairs a handler
 * with the {@link SignalType} it attaches to and a priority. The planner
 * schedules every returned handler against its signal independently, so
 * a single obligation can drive several handlers across different
 * lifecycle points (for example, audit on the decision and a header
 * stamp on the response).
 * <p>
 * Implementations use {@code supportedSignals} to discover which
 * {@link SignalType} instances the deployed PEP actually fires (such as
 * the {@link Signal.OutputSignal} type bound to a concrete value type)
 * and only return handlers whose signal type is in that set.
 */
public interface ConstraintHandlerProvider {

    /**
     * Returns the handlers that enforce {@code constraint}.
     *
     * @param constraint the constraint value from the authorization decision.
     * @param supportedSignals signal types the deployed PEP advertises.
     * @return an empty list when the provider does not handle this
     * constraint, or a non-empty list of scoped handlers to schedule.
     */
    List<ScopedConstraintHandler> getConstraintHandlers(Value constraint, Set<SignalType> supportedSignals);
}
