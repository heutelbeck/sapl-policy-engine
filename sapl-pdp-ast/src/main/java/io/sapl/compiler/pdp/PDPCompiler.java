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

import io.sapl.api.pdp.PDPConfiguration;
import io.sapl.ast.Policy;
import io.sapl.ast.PolicySet;
import io.sapl.ast.SaplDocument;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.policy.PolicyCompiler;
import io.sapl.compiler.policyset.PolicySetCompiler;
import io.sapl.compiler.targetexpression.SchemaValidatorCompiler;
import lombok.experimental.UtilityClass;
import lombok.val;

@UtilityClass
public class PDPCompiler {
    public static Object compilePDPConfiguration(PDPConfiguration pdpConfiguration, CompilationContext ctx) {
        return null;
    }

    public CompiledDocument compileDocument(SaplDocument saplDocument, CompilationContext ctx) {
        val schemaValidator = SchemaValidatorCompiler.compileValidator(saplDocument.schemas(), ctx);
        return switch (saplDocument.element()) {
        case Policy policy       -> PolicyCompiler.compilePolicy(policy, schemaValidator, ctx);
        case PolicySet policySet -> PolicySetCompiler.compilePolicySet(policySet, schemaValidator, ctx);
        };
    }
}
