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

import io.sapl.compiler.document.Voter;

/**
 * Compiled form of a PDP configuration: pure data describing the
 * combined PDP-level voter and its identity. Mirrors the per-document
 * compile shape ({@link io.sapl.compiler.document.CompiledDocument}):
 * metadata plus the production {@link Voter}.
 * <p>
 * Evaluation collaborators (function broker, timestamp source,
 * subscription IDs) and the snapshot trigger pipeline live in the eval
 * loop, not in this record. A future PDP-level {@code CoverageVoter}
 * peer can be added when PDP-level coverage aggregation is implemented;
 * until then the eval loop drives only the production {@link Voter}.
 *
 * @param metadata the voter identity (pdpId, configId, combining algorithm,
 * outcome)
 * @param voter the compiled root voter for this PDP configuration
 */
public record CompiledPdp(PdpVoterMetadata metadata, Voter voter) {}
