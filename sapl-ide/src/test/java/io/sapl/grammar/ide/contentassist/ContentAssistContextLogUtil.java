/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.grammar.ide.contentassist;

import org.apache.commons.text.StringEscapeUtils;
import org.eclipse.xtext.RuleCall;
import org.eclipse.xtext.TerminalRule;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistContext;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class ContentAssistContextLogUtil {
    public static void dump(final ContentAssistContext context) {
        var n = ContextAnalyzer.firstNodeForAnalysis(context);
        var t = new StringBuilder();
        var c = new StringBuilder();
        var g = new StringBuilder();
        var r = new StringBuilder();
        var s = new StringBuilder();
        while (null != n) {
            final var text  = StringEscapeUtils.escapeJava(n.getText());
            final var clazz = StringEscapeUtils.escapeJava(n.getClass().getSimpleName());
            final var ge    = StringEscapeUtils
                    .escapeJava(n.getGrammarElement() == null ? "null" : n.getGrammarElement().eClass().getName());
            var       ru    = "";
            if (n.getGrammarElement() instanceof final RuleCall ruleCall) {
                ru = ruleCall.getRule().getName();
            } else if (n.getGrammarElement() instanceof final TerminalRule rule) {
                ru = rule.getName();
            }
            final var se     = StringEscapeUtils
                    .escapeJava(n.getSemanticElement() == null ? "null" : n.getSemanticElement().eClass().getName());
            final var length = Math.max(
                    Math.max(Math.max(text.length(), clazz.length()), Math.max(ge.length(), se.length())), ru.length());
            final var format = "%" + length + "s|";
            t.insert(0, String.format(format, text));
            c.insert(0, String.format(format, clazz));
            g.insert(0, String.format(format, ge));
            r.insert(0, String.format(format, ru));
            s.insert(0, String.format(format, se));
            n = ContextAnalyzer.leftOf(n);
        }
        log.trace("Text    : {}", t);
        log.trace("Class   : {}", c);
        log.trace("Grammar : {}", g);
        log.trace("Rule    : {}", r);
        log.trace("Semantic: {}", s);
    }
}
