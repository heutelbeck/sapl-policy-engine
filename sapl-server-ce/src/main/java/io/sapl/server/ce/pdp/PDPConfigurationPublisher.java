/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.server.ce.pdp;

import java.util.Collection;

import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.server.ce.model.pdpconfiguration.Variable;
import lombok.NonNull;

/**
 * Publisher for changed configuration of the PDP.
 */
public interface PDPConfigurationPublisher {
    /**
     * Publishes a changed {@link PolicyDocumentCombiningAlgorithm}.
     *
     * @param algorithm the changed {@link PolicyDocumentCombiningAlgorithm}
     */
    void publishCombiningAlgorithm(@NonNull PolicyDocumentCombiningAlgorithm algorithm);

    /**
     * Publishes a changed collection of {@link Variable} instances.
     *
     * @param variables the collection of {@link Variable} instances
     */
    void publishVariables(@NonNull Collection<Variable> variables);
}
