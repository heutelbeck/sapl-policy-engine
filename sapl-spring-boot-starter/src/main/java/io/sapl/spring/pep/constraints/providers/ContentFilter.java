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
package io.sapl.spring.pep.constraints.providers;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.reactivestreams.Publisher;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.MapFunction;
import com.jayway.jsonpath.PathNotFoundException;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import org.springframework.security.access.AccessDeniedException;
import io.sapl.spring.pep.constraints.ConstraintHandler.Mapper;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

@UtilityClass
public class ContentFilter {

    private static final String ERROR_CONDITIONS_NOT_AN_ARRAY                 = "'conditions' not an array: ";
    private static final String ERROR_CONSTRAINT_INVALID                      = "Not a valid constraint. Expected an object value";
    private static final String ERROR_CONSTRAINT_PATH_NOT_PRESENT             = "Error evaluating a constraint predicate. The path defined in the constraint is not present in the data.";
    private static final String ERROR_CONSTRAINT_PATH_NOT_PRESENT_ENFORCEMENT = "Constraint enforcement failed. Error evaluating a constraint predicate. The path defined in the constraint is not present in the data.";
    private static final String ERROR_CONVERTING_MODIFIED_OBJECT              = "Error converting modified object to original class type.";
    private static final String ERROR_PREDICATE_CONDITION_INVALID             = "Not a valid predicate condition: ";
    private static final String ERROR_REGEX_UNSAFE                            = "Unsafe regex pattern rejected (potential ReDoS): ";

    private static final String DISCLOSE_LEFT                  = "discloseLeft";
    private static final String DISCLOSE_RIGHT                 = "discloseRight";
    private static final String REPLACEMENT                    = "replacement";
    private static final String REPLACE                        = "replace";
    private static final String LENGTH                         = "length";
    private static final String BLACKEN                        = "blacken";
    private static final String DELETE                         = "delete";
    private static final String PATH                           = "path";
    private static final String ACTIONS                        = "actions";
    private static final String CONDITIONS                     = "conditions";
    private static final String VALUE                          = "value";
    private static final String EQUALS                         = "==";
    private static final String NEQ                            = "!=";
    private static final String GEQ                            = ">=";
    private static final String LEQ                            = "<=";
    private static final String GT                             = ">";
    private static final String LT                             = "<";
    private static final String REGEX                          = "=~";
    private static final String TYPE                           = "type";
    private static final String BLACK_SQUARE                   = "█";
    private static final String ERROR_ACTION_NOT_AN_OBJECT     = "An action in 'actions' is not an object.";
    private static final String ERROR_ACTIONS_NOT_AN_ARRAY     = "'actions' is not an array.";
    private static final String ERROR_LENGTH_NOT_NUMBER        = "'length' of 'blacken' action is not numeric.";
    private static final String ERROR_NO_REPLACEMENT_SPECIFIED = "The constraint indicates a text node to be replaced. However, the action does not specify a 'replacement'.";
    private static final String ERROR_PATH_NOT_TEXTUAL         = "The constraint indicates a text node to be blackened. However, the node identified by the path is not a text note.";
    private static final String ERROR_REPLACEMENT_NOT_TEXTUAL  = "'replacement' of 'blacken' action is not textual.";
    private static final String ERROR_UNKNOWN_ACTION_S         = "Unknown action type: '%s'.";
    private static final String ERROR_VALUE_NOT_INTEGER_S      = "An action's '%s' is not an integer.";
    private static final String ERROR_VALUE_NOT_TEXTUAL_S      = "An action's '%s' is not textual.";
    private static final int    BLACKEN_LENGTH_INVALID_VALUE   = -1;

    private static final Pattern REDOS_ALTERNATION_WITH_QUANT = Pattern.compile("\\([^)|]*+\\|[^)]*+\\)[*+]");
    private static final Pattern REDOS_NESTED_BOUNDED_QUANT   = Pattern.compile("\\{\\d+,\\d*}[^{]*\\{\\d+,\\d*}");
    private static final Pattern REDOS_NESTED_QUANTIFIERS     = Pattern.compile("\\([^)]*[*+]\\)[*+]");
    private static final Pattern REDOS_NESTED_WILDCARDS       = Pattern.compile("\\([^)*]*+\\*[^)]*+\\)[^)*]*+\\*");

    /**
     * Builds a typed mapper that filters JSON content of the payload according to
     * {@code constraint}.
     * The constraint must be an {@link ObjectValue} with optional
     * {@code conditions} (array of predicates)
     * and {@code actions} (array of redaction/blacken/replace actions). Payloads of
     * type {@link Optional},
     * {@link List}, {@link Set}, {@link Object Object[]}, and reactive
     * {@link Publisher} are filtered
     * elementwise; any other payload is filtered as a single element.
     * </p>
     *
     * @param constraint the SAPL constraint value (an object with
     * conditions/actions)
     * @param objectMapper Jackson mapper used to round-trip payloads through native
     * Java types for JsonPath
     * @return a mapper from {@code Object} to {@code Object}; collection-shaped
     * payloads round-trip per element using each element's runtime class.
     */
    public static Mapper<Object> getHandler(Value constraint, ObjectMapper objectMapper) {
        val predicate      = predicateFromConditions(constraint, objectMapper);
        val transformation = getTransformationHandler(constraint, objectMapper);
        return payload -> switch (payload) {
        case null                 -> null;
        case Optional<?> optional -> optional.map(x -> mapElement(x, transformation, predicate));
        case List<?> list         -> mutableList(list.stream().map(x -> mapElement(x, transformation, predicate)));
        case Set<?> set           ->
            set.stream().map(x -> mapElement(x, transformation, predicate)).collect(Collectors.toSet());
        case Mono<?> mono         -> mono.map(x -> mapElement(x, transformation, predicate));
        case Flux<?> flux         -> flux.map(x -> mapElement(x, transformation, predicate));
        case Object[] array       -> mapArrayContents(array, transformation, predicate);
        default                   -> mapElement(payload, transformation, predicate);
        };
    }

    /**
     * Builds a mapper that filters out elements not matching the constraint's
     * predicate. Mirrors the payload-shape dispatch of
     * {@link #getHandler(Value, ObjectMapper)} but applies the predicate as
     * an element filter (drops non-matching elements) rather than guarding a
     * transformation.
     *
     * @param constraint the SAPL constraint value carrying the {@code conditions}
     * @param objectMapper Jackson mapper used to evaluate JsonPath expressions in
     * conditions
     * @return a mapper from {@code Object} to {@code Object}
     */
    @SuppressWarnings("unchecked")
    public static Mapper<Object> getFilterPredicateHandler(Value constraint, ObjectMapper objectMapper) {
        val predicate = predicateFromConditions(constraint, objectMapper);
        return payload -> switch (payload) {
        case null                 -> null;
        case Optional<?> optional -> ((Optional<Object>) optional).filter(predicate);
        case List<?> list         -> mutableList(((List<Object>) list).stream().filter(predicate));
        case Set<?> set           -> ((Set<Object>) set).stream().filter(predicate).collect(Collectors.toSet());
        case Mono<?> mono         -> ((Mono<Object>) mono).filter(predicate);
        case Flux<?> flux         -> ((Flux<Object>) flux).filter(predicate);
        case Object[] array       -> filterArrayContents(array, predicate);
        default                   -> predicate.test(payload) ? payload : null;
        };
    }

    /*
     * Use this in place of Stream#toList() when collecting list-shaped payload
     * contents. Axon Server cannot
     * deserialise the immutable ListN/List12 classes that Stream#toList() returns,
     * so the integration breaks
     * if a downstream Axon stage receives such a list. Collectors.toList() yields a
     * mutable ArrayList that
     * Axon can handle.
     */
    private static <T> List<T> mutableList(java.util.stream.Stream<T> stream) {
        return stream.collect(Collectors.toList());
    }

    private static Object filterArrayContents(Object[] array, Predicate<Object> predicate) {
        val filteredAsList = Arrays.stream(array).filter(predicate).toList();
        return rebuildArray(array, filteredAsList);
    }

    private static Object mapArrayContents(Object[] array, UnaryOperator<Object> transformation,
            Predicate<Object> predicate) {
        val mappedAsList = Arrays.stream(array).map(o -> mapElement(o, transformation, predicate)).toList();
        return rebuildArray(array, mappedAsList);
    }

    private static Object rebuildArray(Object[] template, List<?> contents) {
        val resultArray = Array.newInstance(template.getClass().getComponentType(), contents.size());
        var i           = 0;
        for (var x : contents) {
            Array.set(resultArray, i++, x);
        }
        return resultArray;
    }

    private static Object mapElement(Object payload, UnaryOperator<Object> transformation,
            Predicate<Object> predicate) {
        if (predicate.test(payload)) {
            return transformation.apply(payload);
        }
        return payload;
    }

    static Predicate<Object> predicateFromConditions(Value constraint, ObjectMapper objectMapper) {
        val constraintObject = requireObject(constraint);
        val conditionsValue  = constraintObject.get(CONDITIONS);
        if (conditionsValue == null) {
            return anything -> true;
        }
        if (!(conditionsValue instanceof ArrayValue conditions)) {
            throw new AccessDeniedException(ERROR_CONDITIONS_NOT_AN_ARRAY + conditionsValue);
        }
        Predicate<Object> combined = anything -> true;
        for (var condition : conditions) {
            val newPredicate      = conditionToPredicate(condition, objectMapper);
            val previousPredicate = combined;
            combined = x -> previousPredicate.test(x) && newPredicate.test(x);
        }
        val finalPredicate = combined;
        return x -> {
            try {
                return finalPredicate.test(x);
            } catch (PathNotFoundException e) {
                throw new AccessDeniedException(ERROR_CONSTRAINT_PATH_NOT_PRESENT, e);
            }
        };
    }

    private static ObjectValue requireObject(Value constraint) {
        if (!(constraint instanceof ObjectValue object)) {
            throw new AccessDeniedException(ERROR_CONSTRAINT_INVALID);
        }
        return object;
    }

    private static Predicate<Object> conditionToPredicate(Value condition, ObjectMapper objectMapper) {
        if (!(condition instanceof ObjectValue conditionObject)) {
            throw new AccessDeniedException(ERROR_PREDICATE_CONDITION_INVALID + condition);
        }
        if (!(conditionObject.get(PATH) instanceof TextValue(var path))) {
            throw new AccessDeniedException(ERROR_PREDICATE_CONDITION_INVALID + condition);
        }
        if (!(conditionObject.get(TYPE) instanceof TextValue(var type))) {
            throw new AccessDeniedException(ERROR_PREDICATE_CONDITION_INVALID + condition);
        }
        val conditionValue = conditionObject.get(VALUE);
        if (conditionValue == null) {
            throw new AccessDeniedException(ERROR_PREDICATE_CONDITION_INVALID + condition);
        }
        return switch (type) {
        case EQUALS -> equalsCondition(condition, path, conditionValue, objectMapper);
        case NEQ    -> Predicate.not(equalsCondition(condition, path, conditionValue, objectMapper));
        case GEQ    -> numericCondition(condition, path, conditionValue, objectMapper, (a, b) -> a >= b);
        case LEQ    -> numericCondition(condition, path, conditionValue, objectMapper, (a, b) -> a <= b);
        case LT     -> numericCondition(condition, path, conditionValue, objectMapper, (a, b) -> a < b);
        case GT     -> numericCondition(condition, path, conditionValue, objectMapper, (a, b) -> a > b);
        case REGEX  -> regexCondition(condition, path, conditionValue, objectMapper);
        default     -> throw new AccessDeniedException(ERROR_PREDICATE_CONDITION_INVALID + condition);
        };
    }

    @FunctionalInterface
    private interface DoubleComparison {
        boolean test(double payloadValue, double conditionValue);
    }

    private static Predicate<Object> numericCondition(Value condition, String path, Value conditionValue,
            ObjectMapper objectMapper, DoubleComparison comparison) {
        if (!(conditionValue instanceof NumberValue(var conditionNumber))) {
            throw new AccessDeniedException(ERROR_PREDICATE_CONDITION_INVALID + condition);
        }
        val threshold = conditionNumber.doubleValue();
        return original -> {
            val value = getValueAtPath(original, path, objectMapper);
            if (!(value instanceof Number numberValue)) {
                return false;
            }
            return comparison.test(numberValue.doubleValue(), threshold);
        };
    }

    private static Predicate<Object> regexCondition(Value condition, String path, Value conditionValue,
            ObjectMapper objectMapper) {
        if (!(conditionValue instanceof TextValue(var patternText))) {
            throw new AccessDeniedException(ERROR_PREDICATE_CONDITION_INVALID + condition);
        }
        if (isDangerousRegex(patternText)) {
            throw new AccessDeniedException(ERROR_REGEX_UNSAFE + patternText);
        }
        val regex = Pattern.compile(patternText);
        return original -> {
            val value = getValueAtPath(original, path, objectMapper);
            if (!(value instanceof String stringValue)) {
                return false;
            }
            return regex.asMatchPredicate().test(stringValue);
        };
    }

    private static Predicate<Object> equalsCondition(Value condition, String path, Value conditionValue,
            ObjectMapper objectMapper) {
        return switch (conditionValue) {
        case NumberValue(var bd) -> {
            val threshold = bd.doubleValue();
            yield original -> getValueAtPath(original, path, objectMapper) instanceof Number n
                    && n.doubleValue() == threshold;
        }
        case TextValue(var s)    ->
            original -> getValueAtPath(original, path, objectMapper) instanceof String str && s.equals(str);
        default                  -> throw new AccessDeniedException(ERROR_PREDICATE_CONDITION_INVALID + condition);
        };
    }

    private static Object getValueAtPath(Object original, String path, ObjectMapper objectMapper) {
        // Convert to native Java types for jsonpath compatibility
        val originalAsNative = objectMapper.convertValue(original, Object.class);
        val jsonContext      = JsonPath.parse(originalAsNative);
        return jsonContext.read(path);
    }

    static UnaryOperator<Object> getTransformationHandler(Value constraint, ObjectMapper objectMapper) {
        val constraintObject = requireObject(constraint);
        return original -> {
            val actionsValue = constraintObject.get(ACTIONS);
            if (actionsValue == null) {
                return original;
            }
            if (!(actionsValue instanceof ArrayValue actions)) {
                throw new AccessDeniedException(ERROR_ACTIONS_NOT_AN_ARRAY);
            }

            // Convert to native Java types (Map/List) for jsonpath compatibility
            val originalAsNative = objectMapper.convertValue(original, Object.class);
            val jsonContext      = JsonPath.parse(originalAsNative);

            for (var action : actions) {
                applyAction(jsonContext, action, objectMapper);
            }

            Object modifiedNative = jsonContext.json();
            try {
                return objectMapper.convertValue(modifiedNative, original.getClass());
            } catch (IllegalArgumentException e) {
                throw new AccessDeniedException(ERROR_CONVERTING_MODIFIED_OBJECT, e);
            }
        };
    }

    private static void applyAction(DocumentContext jsonContext, Value action, ObjectMapper objectMapper) {
        if (!(action instanceof ObjectValue actionObject)) {
            throw new AccessDeniedException(ERROR_ACTION_NOT_AN_OBJECT);
        }
        val path       = getTextualValueOfActionKey(actionObject, PATH);
        val actionType = getTextualValueOfActionKey(actionObject, TYPE).trim().toLowerCase();
        try {
            jsonContext.read(path);
        } catch (PathNotFoundException e) {
            throw new AccessDeniedException(ERROR_CONSTRAINT_PATH_NOT_PRESENT_ENFORCEMENT, e);
        }
        switch (actionType) {
        case DELETE  -> jsonContext.delete(path);
        case BLACKEN -> blacken(jsonContext, path, actionObject);
        case REPLACE -> replace(jsonContext, path, actionObject, objectMapper);
        default      -> throw new AccessDeniedException(ERROR_UNKNOWN_ACTION_S.formatted(actionType));
        }
    }

    private static void replace(DocumentContext jsonContext, String path, ObjectValue action,
            ObjectMapper objectMapper) {
        jsonContext.map(path, replaceNode(action, objectMapper));
    }

    private static MapFunction replaceNode(ObjectValue action, ObjectMapper objectMapper) {
        return (original, configuration) -> {
            val replacement = action.get(REPLACEMENT);
            if (replacement == null) {
                throw new AccessDeniedException(ERROR_NO_REPLACEMENT_SPECIFIED);
            }
            return objectMapper.convertValue(replacement, Object.class);
        };
    }

    private static void blacken(DocumentContext jsonContext, String path, ObjectValue action) {
        jsonContext.map(path, blackenNode(action));
    }

    private static MapFunction blackenNode(ObjectValue action) {
        return (original, configuration) -> {
            if (!(original instanceof String originalString)) {
                throw new AccessDeniedException(ERROR_PATH_NOT_TEXTUAL);
            }
            val replacementString = determineReplacementString(action);
            val discloseRight     = getIntegerValueOfActionKeyOrDefaultToZero(action, DISCLOSE_RIGHT);
            val discloseLeft      = getIntegerValueOfActionKeyOrDefaultToZero(action, DISCLOSE_LEFT);
            val blackenLength     = determineBlackenLength(action);
            return blackenUtil(originalString, replacementString, discloseRight, discloseLeft, blackenLength);
        };
    }

    private static int determineBlackenLength(ObjectValue action) {
        return switch (action.get(LENGTH)) {
        case null                                      -> BLACKEN_LENGTH_INVALID_VALUE;
        case NumberValue(var bd) when bd.signum() >= 0 -> bd.intValue();
        default                                        -> throw new AccessDeniedException(ERROR_LENGTH_NOT_NUMBER);
        };
    }

    private static String blackenUtil(String originalString, String replacement, int discloseRight, int discloseLeft,
            int blackenLength) {
        if (discloseLeft + discloseRight >= originalString.length()) {
            return originalString;
        }
        val result = new StringBuilder();
        if (discloseLeft > 0) {
            result.append(originalString, 0, discloseLeft);
        }
        val replacedChars      = originalString.length() - discloseLeft - discloseRight;
        val blackenFinalLength = (blackenLength == BLACKEN_LENGTH_INVALID_VALUE) ? replacedChars : blackenLength;
        result.repeat(replacement, blackenFinalLength);
        if (discloseRight > 0) {
            result.append(originalString.substring(discloseLeft + replacedChars));
        }
        return result.toString();
    }

    private static String determineReplacementString(ObjectValue action) {
        return switch (action.get(REPLACEMENT)) {
        case null             -> BLACK_SQUARE;
        case TextValue(var s) -> s;
        default               -> throw new AccessDeniedException(ERROR_REPLACEMENT_NOT_TEXTUAL);
        };
    }

    private static String getTextualValueOfActionKey(ObjectValue action, String key) {
        if (!(action.get(key) instanceof TextValue(var value))) {
            throw new AccessDeniedException(ERROR_VALUE_NOT_TEXTUAL_S.formatted(key));
        }
        return value;
    }

    private static int getIntegerValueOfActionKeyOrDefaultToZero(ObjectValue action, String key) {
        val value = action.get(key);
        if (value == null) {
            return 0;
        }
        if (!(value instanceof NumberValue(var bd))) {
            throw new AccessDeniedException(ERROR_VALUE_NOT_INTEGER_S.formatted(key));
        }
        try {
            return bd.intValueExact();
        } catch (ArithmeticException e) {
            throw new AccessDeniedException(ERROR_VALUE_NOT_INTEGER_S.formatted(key), e);
        }
    }

    private static boolean isDangerousRegex(String pattern) {
        return REDOS_NESTED_QUANTIFIERS.matcher(pattern).find() || REDOS_ALTERNATION_WITH_QUANT.matcher(pattern).find()
                || REDOS_NESTED_WILDCARDS.matcher(pattern).find() || REDOS_NESTED_BOUNDED_QUANT.matcher(pattern).find()
                || pattern.contains(".*.*") || pattern.contains(".+.+");
    }
}
