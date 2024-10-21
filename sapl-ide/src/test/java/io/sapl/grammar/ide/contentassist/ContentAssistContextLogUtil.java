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
        var t = "";
        var c = "";
        var g = "";
        var r = "";
        var s = "";
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
            t = String.format(format, text) + t;
            c = String.format(format, clazz) + c;
            g = String.format(format, ge) + g;
            r = String.format(format, ru) + r;
            s = String.format(format, se) + s;
            n = ContextAnalyzer.leftOf(n);
        }
        log.trace("Text    : {}", t);
        log.trace("Class   : {}", c);
        log.trace("Grammar : {}", g);
        log.trace("Rule    : {}", r);
        log.trace("Semantic: {}", s);
    }
}
