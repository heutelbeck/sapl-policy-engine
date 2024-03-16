package io.sapl.grammar;

import java.io.IOException;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.Expression;
import io.sapl.testutil.ParserUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class SourceLinkiningExperimentTests {

    @Test
    void testIt() throws IOException {
        printError("1 \n+\n (2/\n0)", false);
        printError("1 + (2/    0)", true);
    }

    @SneakyThrows
    private void printError(String expressionSource, boolean enumerateLines) {
        Expression expression = ParserUtil.expression(expressionSource);
        var        result     = expression.evaluate().blockFirst();
        log.error("result              : {}", result);
        // dumpNodeInfo((EObject) result.getErrorSource());
        multiLog(errorReport(result, enumerateLines));
    }

    void dumpNodeInfo(EObject o) {
        INode nodeSet = NodeModelUtils.getNode(o);
        log.error("text                : '{}'", nodeSet.getText());
        log.error("toString            : {}", nodeSet.toString());
        log.error("startLine           : {}", nodeSet.getStartLine());
        log.error("endLine             : {}", nodeSet.getEndLine());
        log.error("endOffset           : {}", nodeSet.getEndOffset());
        log.error("grammarElement      : {}", nodeSet.getGrammarElement());
        log.error("length              : {}", nodeSet.getLength());
        log.error("offset              : {}", nodeSet.getOffset());
        log.error("root node           : {}", nodeSet.getRootNode());
        log.error("syntax error message: {}", nodeSet.getSyntaxErrorMessage());
        log.error("totalendline        : {}", nodeSet.getTotalEndLine());
        log.error("TotalEndOffset      : {}", nodeSet.getTotalEndOffset());
        log.error("TotalLength         : {}", nodeSet.getTotalLength());
        log.error("TotalOffset         : {}", nodeSet.getTotalOffset());
        log.error("TotalStartLine      : {}", nodeSet.getTotalStartLine());
    }

    private String errorReport(Val error, boolean enumerateLines) {
        if (!error.isError()) {
            return "No error";
        }
        var report = "";
        if (error.getErrorSourceReference() instanceof EObject errorSource) {
            var markedSource = markErrorSource(errorSource, enumerateLines);
            report += markErrorSource(errorSource, enumerateLines).markedSource();
            report += String.format("Error (%d,%d): %s", markedSource.row(), markedSource.column(), error.getMessage());
        } else {
            report += "Error: " + error.getMessage();
        }
        return report;
    }

    private record MarkedSource(String markedSource, int row, int column) {
    }

    private MarkedSource markErrorSource(EObject errorSource, boolean enumerateLines) {
        var nodeSet = NodeModelUtils.getNode(errorSource);
        var start   = nodeSet.getOffset();
        var end     = nodeSet.getEndOffset();

        var root                   = nodeSet.getRootNode();
        var documentSource         = root.getText();
        var lines                  = documentSource.split("\n");
        var currentLineStartOffset = 0;
        var markedSource           = "";
        var lineNumber             = 1;
        var column                 = 0;
        var row                    = 0;
        for (var line : lines) {
            var currentLineEndOffset = currentLineStartOffset + line.length();
            if (enumerateLines) {
                markedSource += String.format("(%3d) %s\n", lineNumber, line);
            } else {
                markedSource += line + "\n";
            }
            if ((currentLineStartOffset < start && currentLineEndOffset > start)
                    || (currentLineStartOffset < end && currentLineEndOffset > end)) {
                if (enumerateLines) {
                    markedSource += "      ";
                }
                for (var i = 0; i < line.length(); i++) {
                    var currentOffset = currentLineStartOffset + i;
                    if (currentOffset == start) {
                        row    = lineNumber;
                        column = i + 1;
                    }
                    if (currentOffset == start || currentOffset == end - 1) {
                        markedSource += "^";
                    } else if (currentOffset >= start && currentOffset < end) {
                        markedSource += "-";
                    } else {
                        markedSource += " ";
                    }
                }
                markedSource += "\n";
            }
            lineNumber++;
            currentLineStartOffset = currentLineEndOffset + 1;
        }
        return new MarkedSource(markedSource, row, column);
    }

    void multiLog(String s) {
        for (var line : s.split("\n")) {
            log.info(line);
        }
    }
}
