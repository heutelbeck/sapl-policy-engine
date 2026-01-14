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
import io.sapl.ast.Entitlement;
import io.sapl.compiler.policy.*;
import reactor.core.publisher.Flux;

public record CompiledPolicy(
        Entitlement entitlement,
        CompiledPolicyBody policyBody,
        PolicyCompiler.CompiledConstraints constraints,
        Flux<PolicyDecisionWithCoverage> coverageStream,
        PolicyMetadata metadata) implements CompiledDocument {}
