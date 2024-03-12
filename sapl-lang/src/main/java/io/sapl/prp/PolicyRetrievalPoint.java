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
package io.sapl.prp;

import java.util.Collection;

import reactor.core.publisher.Mono;

/**
 * A policy retrieval point is responsible for selecting all the policies
 * matching a given request.
 */
public interface PolicyRetrievalPoint {

    /**
     * Returns a {@link Mono} of policy retrieval results containing all the
     * policies or policy sets having a target expression that matches the given
     * authorization subscription. The given function context and variables
     * constitute the environment the target expressions are evaluated in.
     *
     * @return a {@link Mono} providing the policy retrieval results containing all
     *         the matching policies or policy sets. New results are only added to
     *         the stream if they are different from the preceding result.
     */
    Mono<PolicyRetrievalResult> retrievePolicies();

    /**
     * @return all loaded documents
     */
    Collection<Document> allDocuments();

    /**
     * @return true, iff no errors in documents, names and ids unique
     */
    boolean isConsistent();
}
