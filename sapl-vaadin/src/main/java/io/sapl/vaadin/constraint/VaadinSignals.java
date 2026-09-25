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
package io.sapl.vaadin.constraint;

import java.util.Set;

import com.vaadin.flow.component.UI;

import io.sapl.spring.pep.constraints.Signal.DecisionSignal;
import io.sapl.spring.pep.constraints.Signal.OutputSignal;
import io.sapl.spring.pep.constraints.SignalType;
import lombok.experimental.UtilityClass;

/**
 * The signals a Vaadin policy enforcement point executes for each decision.
 * <p>
 * The decision signal carries the {@code AuthorizationDecision} and accepts
 * runners and consumers of the decision. The UI signal carries the current
 * {@link UI} as the output value of the enforcement point and accepts
 * consumers of the UI. Constraint handler providers claim the UI signal with
 * {@link #UI_SIGNAL} when it is contained in the supported signals.
 */
@UtilityClass
public class VaadinSignals {

    /**
     * Signal carrying the current Vaadin {@link UI}.
     */
    public static final SignalType UI_SIGNAL = OutputSignal.typeFor(UI.class);

    /**
     * Signals executed by the Vaadin policy enforcement point.
     */
    public static final Set<SignalType> SUPPORTED_SIGNALS = Set.of(DecisionSignal.SIGNAL_TYPE, UI_SIGNAL);

}
