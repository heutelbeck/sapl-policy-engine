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

import io.sapl.pdp.plugins.PluginsBundle;
import io.sapl.compiler.document.Voter;
import io.sapl.compiler.policy.CoverageVoter;

/**
 * Compiled form of a PDP configuration. Carries the voter plus the
 * eval-time {@link PluginsBundle} captured at compile time so every
 * evaluation against this artifact sees a consistent (compile result,
 * plugins) pair. The atomic publish in
 * {@link io.sapl.pdp.configuration.PdpVoterSource} swaps both as one
 * unit.
 *
 * @param metadata the voter identity (pdpId, configId, combining
 * algorithm, outcome)
 * @param voter the compiled root voter for this PDP configuration
 * @param coverageVoter the snapshot-driven coverage-instrumented voter
 * peer
 * @param plugins the plugin-contributed runtime values (function
 * broker, decision interceptors, lifecycle listeners)
 */
public record CompiledPdp(PdpVoterMetadata metadata, Voter voter, CoverageVoter coverageVoter, PluginsBundle plugins) {}
