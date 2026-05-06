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

import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.*;
import io.sapl.api.pdp.configuration.PdpData;
import io.sapl.ast.AttributeStep;
import io.sapl.ast.EnvironmentAttribute;
import io.sapl.ast.Expression;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static io.sapl.api.model.StreamOperator.evalChild;

@UtilityClass
public class AttributeCompiler {

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
    private static final String ERROR_UNDEFINED_ENTITY_IN_ATTRIBUTE_ACCESS    = "Undefined entity in attribute access.";

    private static final ObjectValue DEFAULT_SETTINGS = ObjectValue.builder()
            .put(OPTION_INITIAL_TIMEOUT, Value.of(DEFAULT_TIMEOUT_MS))
            .put(OPTION_POLL_INTERVAL, Value.of(DEFAULT_POLL_INTERVAL_MS))
            .put(OPTION_BACKOFF, Value.of(DEFAULT_BACKOFF_MS)).put(OPTION_RETRIES, Value.of(DEFAULT_RETRIES))
            .put(OPTION_FRESH, Value.FALSE).build();

    public static CompiledExpression compileEnvironmentAttribute(EnvironmentAttribute attr, CompilationContext ctx) {
        return compileAttribute(null, attr.name().full(), attr.arguments(), attr.options(), attr.head(),
                attr.location(), ctx);
    }

    public static CompiledExpression compileAttributeStep(AttributeStep attr, CompilationContext ctx) {
        return compileAttribute(attr.base(), attr.name().full(), attr.arguments(), attr.options(), attr.head(),
                attr.location(), ctx);
    }

    private static CompiledExpression compileAttribute(Expression entityExpr, String attributeName,
            @NonNull List<Expression> arguments, Expression optionsExpr, boolean head, @NonNull SourceLocation location,
            CompilationContext ctx) {

        val options = compileOptions(optionsExpr, ctx);

        CompiledExpression compiledEntity = null;
        if (entityExpr != null) {
            compiledEntity = ExpressionCompiler.compile(entityExpr, ctx);
            if (compiledEntity instanceof ErrorValue err) {
                return err;
            }
        }

        val compiledArgs = new ArrayList<CompiledExpression>(arguments.size());
        for (val argExpr : arguments) {
            val compiled = ExpressionCompiler.compile(argExpr, ctx);
            if (compiled instanceof ErrorValue err) {
                return err;
            }
            compiledArgs.add(compiled);
        }

        return new Attribute(attributeName, compiledEntity, ctx.getData(), compiledArgs, options, head, location);
    }

    /**
     * Compiles attribute finder options by merging policy-level options with
     * PDP-level defaults.
     * <p>
     * Priority chain: policy options &gt; PDP options &gt; built-in defaults.
     *
     * @param optionsExpression the options expression from the policy, or null if
     * none specified
     * @param ctx the compilation context containing PDP-level settings
     * @return an ObjectValue containing the merged options
     * @throws SaplCompilerException if options are invalid, depend on subscription,
     * or use attributes
     */
    public static ObjectValue compileOptions(Expression optionsExpression, CompilationContext ctx) {
        var settings    = DEFAULT_SETTINGS;
        var pdpSettings = ctx.data.variables().get(OPTION_FIELD_ATTRIBUTE_FINDER_OPTIONS);
        if (pdpSettings != null) {
            if (!(pdpSettings instanceof ObjectValue pdpSettingsObjectValue)) {
                throw new SaplCompilerException(
                        ERROR_PDP_DEFAULTS_MUST_BE_OBJECT.formatted(OPTION_FIELD_ATTRIBUTE_FINDER_OPTIONS, pdpSettings),
                        optionsExpression == null ? null : optionsExpression.location());
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

    private static ObjectValue mergeSettings(ObjectValue original, ObjectValue override) {
        val builder = ObjectValue.builder();
        mergeKey(builder, OPTION_INITIAL_TIMEOUT, original, override);
        mergeKey(builder, OPTION_POLL_INTERVAL, original, override);
        mergeKey(builder, OPTION_BACKOFF, original, override);
        mergeKey(builder, OPTION_RETRIES, original, override);
        mergeKey(builder, OPTION_FRESH, original, override);
        return builder.build();
    }

    private static void mergeKey(ObjectValue.Builder builder, String key, ObjectValue original, ObjectValue override) {
        builder.put(key, override.containsKey(key) ? override.get(key) : original.get(key));
    }

    /**
     * Snapshot-driven attribute access.
     * <p>
     * {@link #evaluate(EvaluationContext)} walks entity and every argument via
     * {@link StreamOperator#evalChild}, accumulating dependencies even past
     * any encountered {@link ErrorValue}. Holds the first error and returns
     * it after the full walk completes. {@code null} from a child sets the
     * incomplete flag; on a clean walk with no error the attribute
     * invocation is built and looked up against the snapshot. Precedence at
     * the end: error &gt; null &gt; lookup result.
     */
    public record Attribute(
            String attributeName,
            @Nullable CompiledExpression entity,
            PdpData pdpData,
            List<CompiledExpression> arguments,
            ObjectValue options,
            boolean head,
            SourceLocation location) implements StreamOperator {

        @Override
        public ExpressionResult evaluate(EvaluationContext ctx) {
            val     deps        = HashMap.<SubscriptionKey, List<Occurrence>>newHashMap(arguments.size() + 2);
            boolean seenNull    = false;
            Value   firstError  = null;
            Value   entityValue = null;
            if (entity != null) {
                val v = evalChild(entity, ctx, deps);
                if (v == null) {
                    seenNull = true;
                } else if (v instanceof ErrorValue) {
                    firstError = v;
                } else if (v instanceof UndefinedValue) {
                    firstError = Value.errorAt(location, ERROR_UNDEFINED_ENTITY_IN_ATTRIBUTE_ACCESS);
                } else {
                    entityValue = v;
                }
            }

            val argValues = new ArrayList<Value>(arguments.size());
            for (val arg : arguments) {
                val argValue = evalChild(arg, ctx, deps);
                if (argValue == null) {
                    seenNull = true;
                    continue;
                }
                if (argValue instanceof ErrorValue) {
                    if (firstError == null) {
                        firstError = argValue;
                    }
                    continue;
                }
                argValues.add(argValue);
            }

            if (firstError != null) {
                return new ExpressionResult(firstError, deps);
            }
            if (seenNull) {
                return new ExpressionResult(null, deps);
            }

            val invocation = createInvocation(entityValue, argValues, ctx);
            val key        = new SubscriptionKey(invocation, head);
            deps.computeIfAbsent(key, k -> new ArrayList<>()).add(new Occurrence(location));
            val value = ctx.lookup(key);
            return new ExpressionResult(value, deps);
        }

        private AttributeFinderInvocation createInvocation(Value entityValue, List<Value> argValues,
                EvaluationContext ctx) {
            val timeout      = Duration.ofMillis(longOption(OPTION_INITIAL_TIMEOUT, DEFAULT_TIMEOUT_MS));
            val pollInterval = Duration.ofMillis(longOption(OPTION_POLL_INTERVAL, DEFAULT_POLL_INTERVAL_MS));
            val backoff      = Duration.ofMillis(longOption(OPTION_BACKOFF, DEFAULT_BACKOFF_MS));
            val retries      = longOption(OPTION_RETRIES, DEFAULT_RETRIES);
            val fresh        = freshOption();
            val accessCtx    = new AttributeAccessContext(pdpData.variables(), pdpData.secrets(),
                    ctx.authorizationSubscription().secrets());
            return new AttributeFinderInvocation(ctx.configurationId(), attributeName, entityValue, argValues, timeout,
                    pollInterval, backoff, retries, fresh, accessCtx);
        }

        private long longOption(String key, long defaultValue) {
            val value = options.get(key);
            if (value instanceof NumberValue(BigDecimal n)) {
                return n.longValue();
            }
            return defaultValue;
        }

        private boolean freshOption() {
            val value = options.get(OPTION_FRESH);
            if (value instanceof BooleanValue(boolean b)) {
                return b;
            }
            return false;
        }
    }
}
