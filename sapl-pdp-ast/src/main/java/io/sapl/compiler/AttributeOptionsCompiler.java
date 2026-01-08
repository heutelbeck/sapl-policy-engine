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
package io.sapl.compiler;

import io.sapl.api.model.*;
import io.sapl.ast.Expression;
import lombok.experimental.UtilityClass;
import lombok.val;

@UtilityClass
public class AttributeOptionsCompiler {
    public static final long DEFAULT_TIMEOUT_MS       = 3000L;
    public static final long DEFAULT_POLL_INTERVAL_MS = 30000L;
    public static final long DEFAULT_BACKOFF_MS       = 1000L;
    public static final long DEFAULT_RETRIES          = 3L;

    public static final String OPTION_FIELD_ATTRIBUTE_FINDER_OPTIONS = "attributeFinderOptions";

    public static final String OPTION_INITIAL_TIMEOUT = "initialTimeOutMs";
    public static final String OPTION_POLL_INTERVAL   = "pollIntervalMs";
    public static final String OPTION_BACKOFF         = "backoffMs";
    public static final String OPTION_RETRIES         = "retries";
    public static final String OPTION_FRESH           = "fresh";

    record Options(CompiledExpression policyLocalSettings, SourceLocation location) implements PureOperator {
        @Override
        public Value evaluate(EvaluationContext ctx) {
            // Get PDP-level settings from context variable (may be null)
            var         pdpLocalSettings = ctx.get(OPTION_FIELD_ATTRIBUTE_FINDER_OPTIONS);
            ObjectValue pdpSettings      = null;
            if (pdpLocalSettings != null && !(pdpLocalSettings instanceof UndefinedValue)) {
                if (!(pdpLocalSettings instanceof ObjectValue ov)) {
                    return Value.errorAt(location,
                            "Attribute options in variable '%s' type mismatch. Must be an Object or absent. But got: %s.",
                            OPTION_FIELD_ATTRIBUTE_FINDER_OPTIONS, pdpLocalSettings);
                }
                pdpSettings = ov;
            }

            // Get policy-level settings (may be null if no options expression)
            ObjectValue policySettings = null;
            if (policyLocalSettings instanceof ObjectValue ov) {
                policySettings = ov;
            } else if (policyLocalSettings instanceof PureOperator pureOptions) {
                val value = pureOptions.evaluate(ctx);
                if (value instanceof ErrorValue) {
                    return value;
                }
                if (!(value instanceof ObjectValue ov)) {
                    return Value.errorAt(location, "Attribute options type mismatch. Must be an Object. But got: %s.",
                            value);
                }
                policySettings = ov;
            }
            // null policyLocalSettings is valid - just use PDP settings

            // Merge: policy > PDP (defaults applied in AttributeCompiler)
            val builder = ObjectValue.builder();
            setOption(builder, OPTION_INITIAL_TIMEOUT, policySettings, pdpSettings);
            setOption(builder, OPTION_POLL_INTERVAL, policySettings, pdpSettings);
            setOption(builder, OPTION_BACKOFF, policySettings, pdpSettings);
            setOption(builder, OPTION_RETRIES, policySettings, pdpSettings);
            setOption(builder, OPTION_FRESH, policySettings, pdpSettings);
            return builder.build();
        }

        private void setOption(ObjectValue.Builder builder, String option, ObjectValue policySettings,
                ObjectValue pdpSettings) {
            Value value = null;
            if (policySettings != null) {
                value = policySettings.get(option);
            }
            if (value == null && pdpSettings != null) {
                value = pdpSettings.get(option);
            }
            if (value != null) {
                builder.put(option, value);
            }
        }

        @Override
        public boolean isDependingOnSubscription() {
            return true;
        }
    }

    public CompiledExpression compileOptions(Expression optionsExpression, SourceLocation location,
            CompilationContext ctx) {
        CompiledExpression policyLocalSettings = null;
        if (optionsExpression != null) {
            policyLocalSettings = ExpressionCompiler.compile(optionsExpression, ctx);
        }
        if (policyLocalSettings instanceof ErrorValue) {
            return policyLocalSettings;
        }
        if (policyLocalSettings instanceof StreamOperator) {
            return Value.errorAt(location, "Attribute access not permitted in attribute options.");
        }
        if (policyLocalSettings instanceof Value && !(policyLocalSettings instanceof ObjectValue)) {
            return Value.errorAt(location, "Attribute options type mismatch. Must evaluate to Object. But got: %s.",
                    policyLocalSettings);
        }

        if (policyLocalSettings instanceof ObjectValue ov && setsAllOptionsManually(ov)) {
            return ov;
        }
        return new Options(policyLocalSettings, location);
    }

    private static boolean setsAllOptionsManually(ObjectValue optionsValue) {
        return optionsValue.containsKey(OPTION_INITIAL_TIMEOUT) && optionsValue.containsKey(OPTION_POLL_INTERVAL)
                && optionsValue.containsKey(OPTION_BACKOFF) && optionsValue.containsKey(OPTION_RETRIES)
                && optionsValue.containsKey(OPTION_FRESH);
    }

}
