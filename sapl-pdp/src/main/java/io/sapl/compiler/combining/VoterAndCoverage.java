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
package io.sapl.compiler.combining;

import io.sapl.compiler.document.Voter;
import io.sapl.compiler.policy.CoverageVoter;

/**
 * Compiler output bundle for a policy set: the production {@link Voter}
 * paired with the snapshot-driven {@link CoverageVoter} that emits the
 * matching {@link io.sapl.compiler.document.VoteResultWithCoverage}.
 */
public record VoterAndCoverage(Voter voter, CoverageVoter coverageVoter) {}
