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
package io.sapl.compiler.expressions;

import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.Value;
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

    private static final String ERROR_ATTRIBUTE_ACCESS_NOT_PERMITTED          = "Attribute access not permitted in attribute options.";
    private static final String ERROR_OPTIONS_MUST_BE_OBJECT                  = "Attribute options must be an object, but was: %s.";
    private static final String ERROR_OPTIONS_MUST_NOT_DEPEND_ON_SUBSCRIPTION = "Attribute options must not depend on any element of the authorization subscription";
    private static final String ERROR_PDP_DEFAULTS_MUST_BE_OBJECT             = "If defined, PDP wide defaults (%s) for attribute options must be an object, but was: %s.";

    private static final ObjectValue DEFAULT_SETTINGS = ObjectValue.builder()
            .put(OPTION_INITIAL_TIMEOUT, Value.of(DEFAULT_TIMEOUT_MS))
            .put(OPTION_POLL_INTERVAL, Value.of(DEFAULT_POLL_INTERVAL_MS))
            .put(OPTION_BACKOFF, Value.of(DEFAULT_BACKOFF_MS)).put(OPTION_RETRIES, Value.of(DEFAULT_RETRIES))
            .put(OPTION_FRESH, Value.FALSE).build();

    /**
     * Compiles attribute finder options by merging policy-level options with
     * PDP-level defaults.
     * <p>
     * Priority chain: policy options > PDP options > built-in defaults.
     *
     * @param optionsExpression the options expression from the policy, or null if
     * none specified
     * @param ctx the compilation context containing PDP-level settings
     * @return an ObjectValue containing the merged options
     * @throws SaplCompilerException if options are invalid, depend on subscription,
     * or use attributes
     */
    public CompiledExpression compileOptions(Expression optionsExpression, CompilationContext ctx) {
        var settings    = DEFAULT_SETTINGS;
        var pdpSettings = ctx.data.variables().get(OPTION_FIELD_ATTRIBUTE_FINDER_OPTIONS);
        if (pdpSettings != null) {
            if (!(pdpSettings instanceof ObjectValue pdpSettingsObjectValue)) {
                throw new SaplCompilerException(
                        ERROR_PDP_DEFAULTS_MUST_BE_OBJECT.formatted(OPTION_FIELD_ATTRIBUTE_FINDER_OPTIONS, pdpSettings),
                        optionsExpression.location());
            }
            settings = mergeSettings(settings, pdpSettingsObjectValue);
        }
        if (optionsExpression == null) {
            return settings;
        }
        val policyLocalSettings = ExpressionCompiler.compile(optionsExpression, ctx);
        if (policyLocalSettings instanceof StreamOperator) {
            throw new SaplCompilerException(ERROR_ATTRIBUTE_ACCESS_NOT_PERMITTED, optionsExpression.location());
        }
        if (policyLocalSettings instanceof PureOperator) {
            throw new SaplCompilerException(ERROR_OPTIONS_MUST_NOT_DEPEND_ON_SUBSCRIPTION,
                    optionsExpression.location());
        }
        if (!(policyLocalSettings instanceof ObjectValue localOverrides)) {
            throw new SaplCompilerException(ERROR_OPTIONS_MUST_BE_OBJECT.formatted(policyLocalSettings),
                    optionsExpression.location());
        }
        return mergeSettings(settings, localOverrides);
    }

    private ObjectValue mergeSettings(ObjectValue original, ObjectValue override) {
        val builder = ObjectValue.builder();
        mergeKey(builder, OPTION_INITIAL_TIMEOUT, original, override);
        mergeKey(builder, OPTION_POLL_INTERVAL, original, override);
        mergeKey(builder, OPTION_BACKOFF, original, override);
        mergeKey(builder, OPTION_RETRIES, original, override);
        mergeKey(builder, OPTION_FRESH, original, override);
        return builder.build();
    }

    private void mergeKey(ObjectValue.Builder builder, String key, ObjectValue original, ObjectValue override) {
        builder.put(key, override.containsKey(key) ? override.get(key) : original.get(key));
    }

}
