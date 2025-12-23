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
package io.sapl.api.prp;

import io.sapl.compiler.CompiledPolicy;

import java.util.List;

/**
 * Result of policy retrieval containing matching documents and the total count.
 * <p>
 * The {@code totalDocuments} field represents the total number of documents in
 * the PRP, regardless of whether they
 * matched. This enables auditors to verify that all documents were considered
 * during policy retrieval.
 *
 * @param matches
 * the list of documents whose targets matched the subscription
 * @param totalDocuments
 * the total number of documents in the policy repository
 */
public record MatchingDocuments(List<CompiledPolicy> matches, int totalDocuments) implements PolicyRetrievalResult {}
