/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.grammar.sapl.impl;

import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.checkerframework.checker.regex.qual.Regex;

import io.sapl.api.interpreter.Trace;
import io.sapl.api.interpreter.Val;
import reactor.core.publisher.Flux;

/**
 * Checks for a value matching a regular expression.
 * <p>
 * Grammar: {@code Comparison returns Expression: Prefixed
 * (({Regex.left=current} '=~') right=Prefixed)? ;}
 */
public class RegexImplCustom extends RegexImpl {

    private static final String REGEX_SYNTAX_ERROR = "Syntax error in regular expression '%s'.";

    @Override
    public Flux<Val> evaluate() {
        var leftFlux  = getLeft().evaluate();
        var rightFlux = getRight().evaluate().map(Val::requireText);
        return Flux.combineLatest(leftFlux, rightFlux, this::matchRegexp);
    }

    private Val matchRegexp(Val left, Val right) {
        if (left.isError()) {
            return left;
        }
        if (right.isError()) {
            return right;
        }
        if (!left.isTextual()) {
            return Val.FALSE.withTrace(Regex.class, Map.of(Trace.LEFT, left, Trace.RIGHT, right));
        }
        try {
            return Val.of(Pattern.matches(right.getText(), left.getText())).withTrace(Regex.class,
                    Map.of(Trace.LEFT, left, Trace.RIGHT, right));
        } catch (PatternSyntaxException e) {
            return Val.error(REGEX_SYNTAX_ERROR, right).withTrace(Regex.class,
                    Map.of(Trace.LEFT, left, Trace.RIGHT, right));
        }
    }

}
