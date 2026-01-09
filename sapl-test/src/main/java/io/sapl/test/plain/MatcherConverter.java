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

import io.sapl.api.model.NullValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.test.MockingFunctionBroker.ArgumentMatcher;
import io.sapl.test.grammar.antlr.SAPLTestParser.*;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;

import static io.sapl.compiler.StringsUtil.unquoteString;
import static io.sapl.test.Matchers.*;

/**
 * Converts grammar matcher contexts to ArgumentMatcher instances.
 */
@UtilityClass
class MatcherConverter {

    private static final String ERROR_UNKNOWN_NODE_MATCHER      = "Unknown nodeMatcher type: %s.";
    private static final String ERROR_UNKNOWN_STRING_MATCHER    = "Unknown stringMatcher type: %s.";
    private static final String ERROR_UNKNOWN_STRING_OR_MATCHER = "Unknown stringOrStringMatcher type: %s.";
    private static final String ERROR_UNKNOWN_VAL_MATCHER       = "Unknown valMatcher type: %s.";

    /**
     * Converts a valMatcher context to an ArgumentMatcher.
     *
     * @param ctx the valMatcher context
     * @return the corresponding ArgumentMatcher
     */
    static ArgumentMatcher convert(ValMatcherContext ctx) {
        return switch (ctx) {
        case AnyValMatcherContext ignored          -> any();
        case ValueValMatcherContext valueCtx       -> eq(ValueConverter.convert(valueCtx.value()));
        case MatchingValMatcherContext matchingCtx -> convertNodeMatcher(matchingCtx.nodeMatcher());
        default                                    ->
            throw new IllegalArgumentException(ERROR_UNKNOWN_VAL_MATCHER.formatted(ctx.getClass().getSimpleName()));
        };
    }

    /**
     * Converts a list of valMatcher contexts to a list of ArgumentMatchers.
     *
     * @param matchers the list of valMatcher contexts
     * @return list of ArgumentMatchers
     */
    static List<ArgumentMatcher> convertAll(List<ValMatcherContext> matchers) {
        List<ArgumentMatcher> result = new ArrayList<>(matchers.size());
        for (var matcher : matchers) {
            result.add(convert(matcher));
        }
        return result;
    }

    /**
     * Converts a nodeMatcher context to an ArgumentMatcher.
     */
    static ArgumentMatcher convertNodeMatcher(NodeMatcherContext ctx) {
        return switch (ctx) {
        case NullMatcherContext ignored    -> isNull();
        case TextMatcherContext textCtx    -> convertTextMatcher(textCtx);
        case NumberMatcherContext numCtx   -> convertNumberMatcher(numCtx);
        case BooleanMatcherContext boolCtx -> convertBooleanMatcher(boolCtx);
        case ArrayMatcherContext arrCtx    -> convertArrayMatcher(arrCtx);
        case ObjectMatcherContext objCtx   -> convertObjectMatcher(objCtx);
        default                            ->
            throw new IllegalArgumentException(ERROR_UNKNOWN_NODE_MATCHER.formatted(ctx.getClass().getSimpleName()));
        };
    }

    private static ArgumentMatcher convertTextMatcher(TextMatcherContext ctx) {
        return switch (ctx.stringOrStringMatcher()) {
        case null                                -> anyText();
        case PlainStringMatcherContext plain     -> {
            var expected = unquoteString(plain.text.getText());
            yield matching(v -> v instanceof TextValue t && t.value().equals(expected));
        }
        case ComplexStringMatcherContext complex -> convertStringMatcher(complex.stringMatcher());
        default                                  -> throw new IllegalArgumentException(
                ERROR_UNKNOWN_STRING_OR_MATCHER.formatted(ctx.stringOrStringMatcher().getClass().getSimpleName()));
        };
    }

    private static ArgumentMatcher convertStringMatcher(StringMatcherContext ctx) {
        return switch (ctx) {
        case StringIsNullContext ignored                    -> isNull();
        case StringIsBlankContext ignored                   -> textIsBlank();
        case StringIsEmptyContext ignored                   -> textIsEmpty();
        case StringIsNullOrEmptyContext ignored             ->
            matching(v -> v instanceof NullValue || (v instanceof TextValue t && t.value().isEmpty()));
        case StringIsNullOrBlankContext ignored             ->
            matching(v -> v instanceof NullValue || (v instanceof TextValue t && t.value().isBlank()));
        case StringEqualCompressedWhitespaceContext compCtx -> {
            var expected = unquoteString(compCtx.matchValue.getText());
            yield textEqualsCompressingWhitespace(expected);
        }
        case StringEqualIgnoringCaseContext caseCtx         -> {
            var expected = unquoteString(caseCtx.matchValue.getText());
            yield textEqualsIgnoreCase(expected);
        }
        case StringMatchesRegexContext regexCtx             -> {
            var regex = unquoteString(regexCtx.regex.getText());
            yield textMatching(regex);
        }
        case StringStartsWithContext startsCtx              -> {
            var prefix          = unquoteString(startsCtx.prefix.getText());
            var caseInsensitive = startsCtx.caseInsensitive != null;
            yield caseInsensitive ? textStartingWithIgnoreCase(prefix) : textStartingWith(prefix);
        }
        case StringEndsWithContext endsCtx                  -> {
            var suffix          = unquoteString(endsCtx.postfix.getText());
            var caseInsensitive = endsCtx.caseInsensitive != null;
            yield caseInsensitive ? textEndingWithIgnoreCase(suffix) : textEndingWith(suffix);
        }
        case StringContainsContext containsCtx              -> {
            var substring       = unquoteString(containsCtx.text.getText());
            var caseInsensitive = containsCtx.caseInsensitive != null;
            yield caseInsensitive ? textContainingIgnoreCase(substring) : textContaining(substring);
        }
        case StringContainsInOrderContext orderCtx          -> {
            var substrings = orderCtx.substrings.stream().map(t -> unquoteString(t.getText())).toArray(String[]::new);
            yield textContainsInOrder(substrings);
        }
        case StringWithLengthContext lenCtx                 -> {
            var length = Integer.parseInt(lenCtx.length.getText());
            yield textHasLength(length);
        }
        default                                             ->
            throw new IllegalArgumentException(ERROR_UNKNOWN_STRING_MATCHER.formatted(ctx.getClass().getSimpleName()));
        };
    }

    private static ArgumentMatcher convertNumberMatcher(NumberMatcherContext ctx) {
        if (ctx.number == null) {
            // Just "number" - matches any number
            return anyNumber();
        }
        var expected = Double.parseDouble(ctx.number.getText());
        return numberEqualTo(expected);
    }

    private static ArgumentMatcher convertBooleanMatcher(BooleanMatcherContext ctx) {
        var boolLit = ctx.booleanLiteral();
        if (boolLit == null) {
            // Just "boolean" - matches any boolean
            return anyBoolean();
        }
        var expected = boolLit instanceof TrueLiteralContext;
        return booleanEqualTo(expected);
    }

    private static ArgumentMatcher convertArrayMatcher(ArrayMatcherContext ctx) {
        var body = ctx.arrayMatcherBody();
        if (body == null) {
            // Just "array" - matches any array
            return anyArray();
        }
        // "array where [matcher, matcher, ...]"
        var elementMatchers = body.matchers.stream().map(MatcherConverter::convertNodeMatcher)
                .toArray(ArgumentMatcher[]::new);
        return arrayContainingAll(elementMatchers);
    }

    private static ArgumentMatcher convertObjectMatcher(ObjectMatcherContext ctx) {
        var body = ctx.objectMatcherBody();
        if (body == null) {
            // Just "object" - matches any object
            return anyObject();
        }
        // "object where { "key" is matcher and "key2" is matcher }"
        return matching(v -> {
            if (!(v instanceof ObjectValue obj)) {
                return false;
            }
            for (var pair : body.members) {
                var key = unquoteString(pair.key.getText());
                if (!obj.containsKey(key)) {
                    return false;
                }
                var valueMatcher = convertNodeMatcher(pair.matcher);
                if (!valueMatcher.matches(obj.get(key))) {
                    return false;
                }
            }
            return true;
        });
    }
}
