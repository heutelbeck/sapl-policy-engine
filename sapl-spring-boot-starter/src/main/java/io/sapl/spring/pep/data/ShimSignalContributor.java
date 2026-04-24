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
package io.sapl.spring.pep.data;

import java.util.Set;

import io.sapl.spring.pep.constraints.SignalType;

/**
 * Contributed by backend-specific auto-configurations to declare which shim
 * signal types the deployed wrappers will fire. The PEP collects all
 * contributors and adds their reported signal types to the
 * {@code supportedSignals} set passed to the planner. A constraint that binds
 * a handler to a shim signal whose contributor is not present produces a
 * synthetic failure substitute, so the planner's coverage invariant remains
 * intact.
 */
public interface ShimSignalContributor {

    Set<SignalType> supportedSignals();
}
