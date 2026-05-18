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
package io.sapl.compiler.document;

import io.sapl.compiler.model.Coverage;

/**
 * Per-round outcome of snapshot-driven evaluation of a
 * {@link CompiledDocument}, pairing the {@link VoteResult} with the
 * accumulated {@link Coverage.DocumentCoverage}.
 * <p>
 * Direct snapshot mirror of {@link VoteWithCoverage} where the Vote is
 * replaced by a {@link VoteResult} that may carry a {@code null} vote
 * when the round is incomplete.
 *
 * @since 4.1.0
 */
public record VoteResultWithCoverage(VoteResult voteResult, Coverage.DocumentCoverage coverage) {}
