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

import io.sapl.api.model.CompiledExpression;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.compiler.pdp.CompiledPolicy;
import io.sapl.compiler.pdp.CompiledPolicySet;
import io.sapl.compiler.policy.PolicyMetadata;
import lombok.experimental.UtilityClass;

import java.util.List;

@UtilityClass
public class PermitUnlessDenyCompiler {
    public static CompiledPolicySet compilePermitUnlessDenySet(CompiledExpression targetExpression,
            PolicyMetadata policyMetadata, List<CompiledPolicy> policies, CompilationContext ctx) {
        throw new SaplCompilerException("PermitUnlessDenyCompiler not yet implemented");
    }
}
