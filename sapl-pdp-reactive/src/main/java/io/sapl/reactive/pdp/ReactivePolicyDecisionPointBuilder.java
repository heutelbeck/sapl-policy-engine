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
package io.sapl.reactive.pdp;

import io.sapl.pdp.PDPComponents;
import io.sapl.pdp.PolicyDecisionPointBuilder;
import lombok.experimental.UtilityClass;

/**
 * Static factory that wraps {@link PDPComponents} built by
 * {@link PolicyDecisionPointBuilder} with a
 * {@link DelegatingReactivePolicyDecisionPoint}, yielding
 * {@link ReactivePDPComponents} for consumers that want a Reactor-flavoured
 * interface.
 * <p>
 * No fluent API of its own. All engine setup happens on the core
 * {@link PolicyDecisionPointBuilder}. This is intentionally a thin layer
 * so the core engine stays Reactor-free and the reactive package can be
 * extracted into its own module without dragging Reactor into the core.
 *
 * <pre>{@code
 * var components         = PolicyDecisionPointBuilder.withDefaults().withPolicy(...).build();
 * var reactiveComponents = ReactivePolicyDecisionPointBuilder.from(components);
 * var reactivePdp        = reactiveComponents.pdp();
 * }</pre>
 *
 * @since 4.1.0
 */
@UtilityClass
public class ReactivePolicyDecisionPointBuilder {

    /**
     * Wraps {@code components} with a
     * {@link DelegatingReactivePolicyDecisionPoint}
     * adapter and returns the paired {@link ReactivePDPComponents}.
     *
     * @param components the blocking-engine components built by
     * {@link PolicyDecisionPointBuilder}
     * @return reactive view paired with the underlying components
     */
    public static ReactivePDPComponents from(PDPComponents components) {
        return new ReactivePDPComponents(components, new DelegatingReactivePolicyDecisionPoint(components.pdp()));
    }
}
