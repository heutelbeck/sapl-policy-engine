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
package io.sapl.compiler.index;

import java.util.List;

import io.sapl.api.model.ErrorValue;
import io.sapl.compiler.document.CompiledDocument;

/**
 * Result of a Kleene-compatible policy index lookup. The applicable documents
 * are split by the outcome of their applicability evaluation. The
 * {@code trueMatches} are documents whose applicability resolved to TRUE. The
 * {@code errorMatches} are documents whose applicability resolved to an error,
 * each paired with that error. Documents whose applicability resolved to FALSE
 * are dropped.
 * <p>
 * Unlike {@link PolicyIndexResult}, an erroring document is carried as a
 * candidate together with its error rather than turned into a terminal error
 * vote, so the combining algorithm can compose it with the document's streaming
 * section under Kleene strong three-valued AND and let a streaming FALSE
 * dominate the error.
 *
 * @param trueMatches documents whose applicability evaluated to TRUE
 * @param errorMatches documents whose applicability evaluated to an error
 */
public record PolicyMatches(List<CompiledDocument> trueMatches, List<ErrorMatch> errorMatches) {

    /**
     * A document whose applicability evaluated to an error, paired with that
     * error so the combining algorithm can report it without re-evaluating the
     * applicability predicate.
     *
     * @param document the document whose applicability errored
     * @param error the applicability error
     */
    public record ErrorMatch(CompiledDocument document, ErrorValue error) {}
}
