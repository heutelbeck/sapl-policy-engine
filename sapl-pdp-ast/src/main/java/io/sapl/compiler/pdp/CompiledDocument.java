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
package io.sapl.compiler.pdp;

import io.sapl.api.model.CompiledExpression;

/**
 * A compiled SAPL document (policy or policy set) with multiple evaluation
 * entry points.
 */
public interface CompiledDocument {

    /**
     * @return expression for applicability checking only
     */
    CompiledExpression isApplicable();

    /**
     * @return vote maker assuming applicability (used by PDP after determining
     * applicability)
     */
    Voter voter();

    /**
     * @return vote maker with combined applicability and constraint evaluation
     * (used by policy sets)
     */
    Voter applicabilityAndVote();

    /**
     * @return true if the document has obligations, advice, or a transformation
     */
    boolean hasConstraints();
}
