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
package io.sapl.test.plain;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.test.grammar.antlr.SAPLTestParser.DefaultExtendedMatcherContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.DefaultObjectMatcherContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.ExactMatchObjectMatcherContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.ExtendedObjectMatcherContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.KeyValueObjectMatcherContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.MatchingObjectMatcherContext;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

import static io.sapl.compiler.util.StringsUtil.unquoteString;

@UtilityClass
class DecisionMatcherHelper {

    private static final String ERROR_UNKNOWN_DEFAULT_MATCHER  = "Unknown default matcher type: %s.";
    private static final String ERROR_UNKNOWN_EXTENDED_MATCHER = "Unknown extended matcher type: %s.";

    static boolean matchesObligationConstraint(AuthorizationDecision decision,
            @Nullable ExtendedObjectMatcherContext matcher) {
        var obligations = decision.obligations();
        if (obligations.isEmpty()) {
            return matcher == null;
        }
        if (matcher == null) {
            return true;
        }
        for (var value : obligations) {
            if (matchesExtendedMatcher(value, matcher)) {
                return true;
            }
        }
        return false;
    }

    static boolean matchesAdviceConstraint(AuthorizationDecision decision,
            @Nullable ExtendedObjectMatcherContext matcher) {
        var advice = decision.advice();
        if (advice.isEmpty()) {
            return matcher == null;
        }
        if (matcher == null) {
            return true;
        }
        for (var value : advice) {
            if (matchesExtendedMatcher(value, matcher)) {
                return true;
            }
        }
        return false;
    }

    static boolean matchesResourceConstraint(Value resource, DefaultObjectMatcherContext matcher) {
        return matchesDefaultMatcher(resource, matcher);
    }

    private static boolean matchesExtendedMatcher(Value value, ExtendedObjectMatcherContext ctx) {
        if (ctx instanceof DefaultExtendedMatcherContext defCtx) {
            return matchesDefaultMatcher(value, defCtx.defaultObjectMatcher());
        }
        if (ctx instanceof KeyValueObjectMatcherContext kvCtx) {
            return matchesKeyValueMatcher(value, kvCtx);
        }
        throw new IllegalArgumentException(ERROR_UNKNOWN_EXTENDED_MATCHER.formatted(ctx.getClass().getSimpleName()));
    }

    private static boolean matchesDefaultMatcher(Value value, DefaultObjectMatcherContext ctx) {
        if (ctx instanceof ExactMatchObjectMatcherContext exactCtx) {
            var expected = ValueConverter.convert(exactCtx.equalTo);
            return expected.equals(value);
        }
        if (ctx instanceof MatchingObjectMatcherContext matchCtx) {
            var nodeMatcher = MatcherConverter.convertNodeMatcher(matchCtx.nodeMatcher());
            return nodeMatcher.matches(value);
        }
        throw new IllegalArgumentException(ERROR_UNKNOWN_DEFAULT_MATCHER.formatted(ctx.getClass().getSimpleName()));
    }

    private static boolean matchesKeyValueMatcher(Value value, KeyValueObjectMatcherContext ctx) {
        if (!(value instanceof ObjectValue objValue)) {
            return false;
        }
        var key = unquoteString(ctx.key.getText());
        if (!objValue.containsKey(key)) {
            return false;
        }
        if (ctx.matcher == null) {
            return true;
        }
        var keyValue    = objValue.get(key);
        var nodeMatcher = MatcherConverter.convertNodeMatcher(ctx.matcher);
        return nodeMatcher.matches(keyValue);
    }

}
