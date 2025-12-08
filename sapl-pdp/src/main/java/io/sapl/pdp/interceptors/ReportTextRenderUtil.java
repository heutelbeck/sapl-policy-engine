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
package io.sapl.pdp.interceptors;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.NullValue;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.internal.TraceFields;
import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Utility class for rendering traced decisions as human-readable text reports.
 * <p>
 * Provides formatted output suitable for logging and console display, with
 * optional pretty-printing support using pure Value model formatting.
 */
@UtilityClass
public class ReportTextRenderUtil {

    private static final String DECISION_PREFIX = "Decision    : ";

    /**
     * Renders a report ObjectValue as a human-readable text string.
     *
     * @param report
     * the report ObjectValue from ReportBuilderUtil
     * @param prettyPrint
     * whether to pretty-print JSON values
     * @param mapper
     * the ObjectMapper for JSON formatting
     *
     * @return the formatted text report
     */
    public static String textReport(ObjectValue report, boolean prettyPrint, ObjectMapper mapper) {
        val text = new StringBuilder("--- The PDP made a decision ---\n");

        appendSubscription(text, report, prettyPrint, mapper);
        appendDecision(text, report, prettyPrint, mapper);
        appendTimestamp(text, report);
        appendAlgorithm(text, report);
        appendDocumentCounts(text, report);
        appendRetrievalErrors(text, report);
        appendModifications(text, report);
        appendDocumentReports(text, report);

        return text.toString();
    }

    private static void appendSubscription(StringBuilder text, ObjectValue report, boolean prettyPrint,
            ObjectMapper mapper) {
        val subscription = report.get(TraceFields.SUBSCRIPTION);
        if (subscription != null) {
            text.append("Subscription: ").append(prettyPrint ? '\n' : "")
                    .append(prettyPrintValue(subscription, prettyPrint, mapper)).append('\n');
        }
    }

    private static void appendDecision(StringBuilder text, ObjectValue report, boolean prettyPrint,
            ObjectMapper mapper) {
        val decision    = report.get(TraceFields.DECISION);
        val obligations = report.get(TraceFields.OBLIGATIONS);
        val advice      = report.get(TraceFields.ADVICE);
        val resource    = report.get(TraceFields.RESOURCE);

        text.append(DECISION_PREFIX).append(decision).append('\n');

        if (obligations instanceof ArrayValue arr && !arr.isEmpty()) {
            text.append("Obligations : ").append(prettyPrint ? '\n' : "")
                    .append(prettyPrintValue(obligations, prettyPrint, mapper)).append('\n');
        }
        if (advice instanceof ArrayValue arr && !arr.isEmpty()) {
            text.append("Advice      : ").append(prettyPrint ? '\n' : "")
                    .append(prettyPrintValue(advice, prettyPrint, mapper)).append('\n');
        }
        if (resource != null && !(resource instanceof io.sapl.api.model.UndefinedValue)) {
            text.append("Resource    : ").append(prettyPrint ? '\n' : "")
                    .append(prettyPrintValue(resource, prettyPrint, mapper)).append('\n');
        }
    }

    private static void appendTimestamp(StringBuilder text, ObjectValue report) {
        val timestamp = report.get(TraceFields.TIMESTAMP);
        if (timestamp instanceof TextValue textValue) {
            text.append("Timestamp   : ").append(textValue.value()).append('\n');
        }
    }

    private static void appendAlgorithm(StringBuilder text, ObjectValue report) {
        val algorithm = report.get(TraceFields.ALGORITHM);
        if (algorithm != null) {
            text.append("Algorithm   : ").append(algorithm).append('\n');
        }
    }

    private static void appendDocumentCounts(StringBuilder text, ObjectValue report) {
        val totalDocuments = report.get(TraceFields.TOTAL_DOCUMENTS);
        val documents      = report.get(TraceFields.DOCUMENTS);

        int total    = totalDocuments instanceof io.sapl.api.model.NumberValue num ? num.value().intValue() : 0;
        int matching = documents instanceof ArrayValue arr ? arr.size() : 0;

        text.append("Documents   : ").append(matching).append(" matching out of ").append(total).append(" total\n");
    }

    private static void appendRetrievalErrors(StringBuilder text, ObjectValue report) {
        val retrievalErrors = report.get(TraceFields.RETRIEVAL_ERRORS);
        if (retrievalErrors instanceof ArrayValue errors && !errors.isEmpty()) {
            text.append("Retrieval Errors:\n");
            for (val error : errors) {
                text.append("  - ").append(error).append('\n');
            }
        }
    }

    private static void appendModifications(StringBuilder text, ObjectValue report) {
        val modifications = report.get(TraceFields.MODIFICATIONS);
        if (modifications instanceof ArrayValue mods && !mods.isEmpty()) {
            text.append("Interceptor Modifications:\n");
            for (val mod : mods) {
                if (mod instanceof TextValue textValue) {
                    text.append("  - ").append(textValue.value()).append('\n');
                } else {
                    text.append("  - ").append(mod).append('\n');
                }
            }
        }
    }

    private static void appendDocumentReports(StringBuilder text, ObjectValue report) {
        val documents = report.get(TraceFields.DOCUMENTS);
        if (documents instanceof ArrayValue docs && !docs.isEmpty()) {
            text.append("\n=== Document Evaluation Results ===\n");
            for (val document : docs) {
                if (document instanceof ObjectValue docObj) {
                    appendDocumentReport(text, docObj, "");
                }
            }
        } else {
            text.append("No documents were evaluated.\n");
        }
    }

    private static void appendDocumentReport(StringBuilder text, ObjectValue document, String indent) {
        val type = getTextValue(document, TraceFields.TYPE);
        if (TraceFields.TYPE_POLICY.equals(type)) {
            appendPolicyReport(text, document, indent);
        } else if (TraceFields.TYPE_SET.equals(type)) {
            appendPolicySetReport(text, document, indent);
        }
    }

    private static void appendPolicyReport(StringBuilder text, ObjectValue policy, String indent) {
        text.append(indent).append("Policy: ").append(getTextValue(policy, TraceFields.NAME)).append('\n');
        text.append(indent).append("  Entitlement : ").append(getTextValue(policy, TraceFields.ENTITLEMENT))
                .append('\n');
        text.append(indent).append("  Decision    : ").append(getTextValue(policy, TraceFields.DECISION)).append('\n');

        val errors = policy.get(TraceFields.ERRORS);
        if (errors instanceof ArrayValue errArray && !errArray.isEmpty()) {
            text.append(indent).append("  Errors:\n");
            for (val error : errArray) {
                text.append(indent).append("    - ").append(error).append('\n');
            }
        }

        val attributes = policy.get(TraceFields.ATTRIBUTES);
        if (attributes instanceof ArrayValue attrArray && !attrArray.isEmpty()) {
            text.append(indent).append("  Attributes:\n");
            for (val attribute : attrArray) {
                text.append(indent).append("    - ").append(attribute).append('\n');
            }
        }

        val targetError = policy.get(TraceFields.TARGET_ERROR);
        if (targetError != null) {
            text.append(indent).append("  Target Error: ").append(targetError).append('\n');
        }
    }

    private static void appendPolicySetReport(StringBuilder text, ObjectValue policySet, String indent) {
        text.append(indent).append("Policy Set: ").append(getTextValue(policySet, TraceFields.NAME)).append('\n');
        text.append(indent).append("  Algorithm : ").append(getTextValue(policySet, TraceFields.ALGORITHM))
                .append('\n');
        text.append(indent).append("  Decision  : ").append(getTextValue(policySet, TraceFields.DECISION)).append('\n');

        val policies = policySet.get(TraceFields.POLICIES);
        if (policies instanceof ArrayValue policiesArray && !policiesArray.isEmpty()) {
            text.append(indent).append("  Nested Policies:\n");
            for (val policy : policiesArray) {
                if (policy instanceof ObjectValue policyObj) {
                    appendDocumentReport(text, policyObj, indent + "    ");
                }
            }
        }
    }

    private static String getTextValue(ObjectValue obj, String fieldName) {
        val field = obj.get(fieldName);
        if (field instanceof TextValue textValue) {
            return textValue.value();
        }
        return field != null ? field.toString() : "N/A";
    }

    /**
     * Pretty-prints a Value to a string representation using pure Value model
     * formatting.
     * <p>
     * UndefinedValue and ErrorValue are rendered as terminal nodes using their
     * toString() representation.
     *
     * @param value
     * the Value to format
     * @param prettyPrint
     * whether to apply pretty formatting with indentation
     * @param mapper
     * unused, kept for API compatibility
     *
     * @return the formatted string representation
     */
    public static String prettyPrintValue(Value value, boolean prettyPrint, ObjectMapper mapper) {
        return prettyPrint ? formatValue(value, 0) : formatValueCompact(value);
    }

    private static String formatValue(Value value, int indent) {
        if (value == null) {
            return "null";
        }
        return switch (value) {
        case NullValue n      -> "null";
        case BooleanValue b   -> String.valueOf(b.value());
        case NumberValue n    -> n.value().toPlainString();
        case TextValue t      -> quoteString(t.value());
        case UndefinedValue u -> "undefined";
        case ErrorValue e     -> "error: " + e.message();
        case ArrayValue a     -> formatArray(a, indent);
        case ObjectValue o    -> formatObject(o, indent);
        };
    }

    private static String formatValueCompact(Value value) {
        if (value == null) {
            return "null";
        }
        return switch (value) {
        case NullValue n      -> "null";
        case BooleanValue b   -> String.valueOf(b.value());
        case NumberValue n    -> n.value().toPlainString();
        case TextValue t      -> quoteString(t.value());
        case UndefinedValue u -> "undefined";
        case ErrorValue e     -> "error: " + e.message();
        case ArrayValue a     -> formatArrayCompact(a);
        case ObjectValue o    -> formatObjectCompact(o);
        };
    }

    private static String quoteString(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }

    private static String formatArray(ArrayValue array, int indent) {
        if (array.isEmpty()) {
            return "[]";
        }
        if (isSimpleArray(array)) {
            return formatArrayCompact(array);
        }
        var result      = new StringBuilder("[\n");
        var childIndent = indent + 1;
        var padding     = "  ".repeat(childIndent);
        var first       = true;
        for (var item : array) {
            if (!first) {
                result.append(",\n");
            }
            result.append(padding).append(formatValue(item, childIndent));
            first = false;
        }
        result.append('\n').append("  ".repeat(indent)).append(']');
        return result.toString();
    }

    private static String formatArrayCompact(ArrayValue array) {
        if (array.isEmpty()) {
            return "[]";
        }
        var result = new StringBuilder("[");
        var first  = true;
        for (var item : array) {
            if (!first) {
                result.append(", ");
            }
            result.append(formatValueCompact(item));
            first = false;
        }
        result.append(']');
        return result.toString();
    }

    private static String formatObject(ObjectValue object, int indent) {
        if (object.isSecret()) {
            return "<<SECRET>>";
        }
        if (object.isEmpty()) {
            return "{}";
        }
        var result      = new StringBuilder("{\n");
        var childIndent = indent + 1;
        var padding     = "  ".repeat(childIndent);
        var first       = true;
        for (var entry : object.entrySet()) {
            if (!first) {
                result.append(",\n");
            }
            result.append(padding).append(quoteString(entry.getKey())).append(": ")
                    .append(formatValue(entry.getValue(), childIndent));
            first = false;
        }
        result.append('\n').append("  ".repeat(indent)).append('}');
        return result.toString();
    }

    private static String formatObjectCompact(ObjectValue object) {
        if (object.isSecret()) {
            return "<<SECRET>>";
        }
        if (object.isEmpty()) {
            return "{}";
        }
        var result = new StringBuilder("{");
        var first  = true;
        for (var entry : object.entrySet()) {
            if (!first) {
                result.append(", ");
            }
            result.append(quoteString(entry.getKey())).append(": ").append(formatValueCompact(entry.getValue()));
            first = false;
        }
        result.append('}');
        return result.toString();
    }

    private static boolean isSimpleArray(ArrayValue array) {
        return array.size() <= 3 && array.stream().allMatch(ReportTextRenderUtil::isSimpleValue);
    }

    private static boolean isSimpleValue(Value value) {
        return value instanceof NullValue || value instanceof BooleanValue || value instanceof NumberValue
                || value instanceof TextValue || value instanceof UndefinedValue
                || (value instanceof ArrayValue a && a.isEmpty()) || (value instanceof ObjectValue o && o.isEmpty());
    }
}
