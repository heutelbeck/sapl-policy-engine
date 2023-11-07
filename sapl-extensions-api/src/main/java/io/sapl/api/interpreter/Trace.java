/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.api.interpreter;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import lombok.Value;

/**
 * A policy evaluation trace.
 */
@Value
public class Trace {
    public static final String ADVICE                     = "advice";
    public static final String ARGUMENT                   = "argument";
    public static final String ARGUMENTS_KEY              = "arguments";
    public static final String ATTRIBUTE                  = "attribute";
    public static final String AUTHORIZATION_DECISION     = "authorizationDecision";
    public static final String AUTHORIZATION_SUBSCRIPTION = "authorizationSubscription";
    public static final String CONDITION_EXPRESSION       = "condition expression";
    public static final String COMBINED_DECISION          = "combinedDecision";
    public static final String COMBINING_ALGORITHM        = "combiningAlgorithm";
    public static final String DIVIDEND                   = "dividend";
    public static final String DIVISOR                    = "divisor";
    public static final String DOCUMENT_TYPE              = "documentType";
    public static final String ELEMENT_INDEX              = "elementIndex";
    public static final String ENTITILEMENT               = "entitlement";
    public static final String ERROR_MESSAGE              = "errorMessage";
    public static final String EVALUATED_POLICIES         = "evaluatedPolicies";
    public static final String EXPLANATION                = "explanation";
    public static final String FILTERED                   = "filtered";
    public static final String HAYSTACK                   = "haystack";
    public static final String IDENTIFIER                 = "identifier";
    public static final String INDEX                      = "index";
    public static final String KEY                        = "key";
    public static final String LEFT                       = "left";
    public static final String LEFT_HAND_VALUE            = "leftHandValue";
    public static final String MATCHING_DOCUMENTS         = "matchingDocuments";
    public static final String MINUEND                    = "minuend";
    public static final String MODIFICATIONS              = "modifications";
    public static final String NEEDLE                     = "needle";
    public static final String PARENT_VALUE               = "parentValue";
    public static final String PREVIOUS_CONDITION_RESULT  = "previousConditionResult";
    public static final String OBLIGATIONS                = "obligations";
    public static final String OPERATOR                   = "operator";
    public static final String POLICY_NAME                = "policyName";
    public static final String POLICY_SET_NAME            = "policySetName";
    public static final String RESOURCE                   = "resource";
    public static final String RIGHT                      = "right";
    public static final String SELECTED_INDEX             = "selectedIndex";
    public static final String SUBTRAHEND                 = "subtrahend";
    public static final String TARGET                     = "target";
    public static final String TIMESTAMP                  = "timestamp";
    public static final String TRACE_KEY                  = "trace";
    public static final String UNFILTERED_VALUE           = "unfilteredValue";
    public static final String VALUE                      = "value";
    public static final String VARIABLE_NAME              = "variableName";
    public static final String WHERE                      = "where";

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    Class<?>                 operation;
    List<ExpressionArgument> arguments = new LinkedList<>();

    /**
     * Creates a trace for an operation.
     *
     * @param operation class implementing the traced operation.
     */
    public Trace(Class<?> operation) {
        this.operation = operation;
    }

    /**
     * Creates a trace for an operation and its arguments.
     *
     * @param operation class implementing the traced operation.
     * @param arguments traced arguments.
     */
    public Trace(Class<?> operation, Traced... arguments) {
        this.operation = operation;
        var i = 0;
        for (var argument : arguments) {
            if (arguments.length == 1)
                this.arguments.add(new ExpressionArgument(ARGUMENT, argument));
            else
                this.arguments.add(new ExpressionArgument(ARGUMENTS_KEY + "[" + i++ + "]", argument));
        }
    }

    /**
     * Creates a trace for an operation and its arguments.
     *
     * @param operation class implementing the traced operation.
     * @param arguments traced arguments with parameter names.
     */
    public Trace(Class<?> operation, Map<String, Traced> arguments) {
        this.operation = operation;
        for (var argument : arguments.entrySet()) {
            this.arguments.add(new ExpressionArgument(argument.getKey(), argument.getValue()));
        }
    }

    /**
     * Creates a trace for an operation and its arguments.
     *
     * @param operation class implementing the traced operation.
     * @param arguments traced arguments.
     */
    public Trace(Class<?> operation, ExpressionArgument... arguments) {
        this.operation = operation;
        this.arguments.addAll(Arrays.asList(arguments));
    }

    /**
     * Creates a trace for an attribute finder operation and its arguments.
     *
     * @param leftHandValue the left hand input value of the attribute finder
     * @param operation     class implementing the traced operation.
     * @param arguments     traced arguments.
     */
    public Trace(Traced leftHandValue, Class<?> operation, Traced... arguments) {
        this.operation = operation;
        this.arguments.add(new ExpressionArgument(LEFT_HAND_VALUE, leftHandValue));
        var i = 0;
        for (var argument : arguments) {
            if (arguments.length == 1)
                this.arguments.add(new ExpressionArgument(ARGUMENT, argument));
            else
                this.arguments.add(new ExpressionArgument(ARGUMENTS_KEY + "[" + i++ + "]", argument));
        }
    }

    /**
     * Reads the evaluation trace as a JSON object.
     *
     * @return trace as a JSON object.
     */
    public JsonNode getTrace() {
        var jsonTrace = JSON.objectNode();
        jsonTrace.set(OPERATOR, JSON.textNode(operation.getSimpleName()));
        if (!arguments.isEmpty()) {
            var args = JSON.objectNode();
            for (var argument : arguments)
                args.set(argument.name(), argument.value().getTrace());
            jsonTrace.set(ARGUMENTS_KEY, args);
        }
        return jsonTrace;
    }

    /**
     * @return returns arguments of trace
     */
    public List<ExpressionArgument> getArguments() {
        return Collections.unmodifiableList(arguments);
    }
}
