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
package io.sapl.spring.constraints.providers;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.MapFunction;
import com.jayway.jsonpath.PathNotFoundException;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@UtilityClass
public class ContentFilter {

    private static final String ERROR_CONDITIONS_NOT_AN_ARRAY                 = "'conditions' not an array: ";
    private static final String ERROR_CONSTRAINT_INVALID                      = "Not a valid constraint. Expected a JSON Object";
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
    private static final String ERROR_UNDEFINED_KEY_S          = "An action does not declare '%s'.";
    private static final String ERROR_UNKNOWN_ACTION_S         = "Unknown action type: '%s'.";
    private static final String ERROR_VALUE_NOT_INTEGER_S      = "An action's '%s' is not an integer.";
    private static final String ERROR_VALUE_NOT_TEXTUAL_S      = "An action's '%s' is not textual.";
    private static final int    BLACKEN_LENGTH_INVALID_VALUE   = -1;

    private static final Pattern REDOS_ALTERNATION_WITH_QUANT = Pattern.compile("\\([^)|]*+\\|[^)]*+\\)[*+]");
    private static final Pattern REDOS_NESTED_BOUNDED_QUANT   = Pattern.compile("\\{\\d+,\\d*}[^{]*\\{\\d+,\\d*}");
    private static final Pattern REDOS_NESTED_QUANTIFIERS     = Pattern.compile("\\([^)]*[*+]\\)[*+]");
    private static final Pattern REDOS_NESTED_WILDCARDS       = Pattern.compile("\\([^)*]*+\\*[^)]*+\\)[^)*]*+\\*");

    public static UnaryOperator<Object> getHandler(JsonNode constraint, ObjectMapper objectMapper) {
        final var predicate      = predicateFromConditions(constraint, objectMapper);
        final var transformation = getTransformationHandler(constraint, objectMapper);

        return payload -> {
            switch (payload) {
            case null                   -> {
                return null;
            }
            case Optional<?> optional   -> {
                return optional.map(x -> mapElement(x, transformation, predicate));
            }
            case List<?> list           -> {
                return mapListContents(list, transformation, predicate);
            }
            case Set<?> set             -> {
                return mapSetContents(set, transformation, predicate);
            }
            case Publisher<?> publisher -> {
                return mapPublisherContents(publisher, transformation, predicate);
            }
            case Object[] array         -> {
                final var filteredAsList = mapListContents(Arrays.asList(array), transformation, predicate);
                final var resultArray    = Array.newInstance(payload.getClass().getComponentType(),
                        filteredAsList.size());

                var i = 0;
                for (var x : filteredAsList) {
                    Array.set(resultArray, i++, x);
                }
                return resultArray;
            }
            default                     -> { /* no-op */ }
            }

            return mapElement(payload, transformation, predicate);
        };
    }

    private static Object mapPublisherContents(Publisher<?> payload, UnaryOperator<Object> transformation,
            Predicate<Object> predicate) {
        if (payload instanceof Mono<?> mono) {
            return mono.map(element -> mapElement(element, transformation, predicate));
        }
        return ((Flux<?>) payload).map(element -> mapElement(element, transformation, predicate));
    }

    private static Object mapElement(Object payload, UnaryOperator<Object> transformation,
            Predicate<Object> predicate) {
        if (predicate.test(payload))
            return transformation.apply(payload);

        return payload;
    }

    private static List<?> mapListContents(Collection<?> payload, UnaryOperator<Object> transformation,
            Predicate<Object> predicate) {
        /*
         * Attention: Do not replace with .toList() instead of Collectors.toList(). The
         * Axon integration will break, as Axon Server is not able to handle classes
         * like ListN or List12
         */
        return payload.stream().map(o -> mapElement(o, transformation, predicate)).collect(Collectors.toList());
    }

    private static Set<?> mapSetContents(Collection<?> payload, UnaryOperator<Object> transformation,
            Predicate<Object> predicate) {
        return payload.stream().map(o -> mapElement(o, transformation, predicate)).collect(Collectors.toSet());
    }

    public static Predicate<Object> predicateFromConditions(JsonNode constraint, ObjectMapper objectMapper) {
        assertConstraintIsAnObjectNode(constraint);
        Predicate<Object> predicate = anything -> true;
        if (noConditionsPresent(constraint))
            return predicate;

        assertConditionsIsAnArrayNode(constraint);

        final var conditions = (ArrayNode) constraint.get(CONDITIONS);
        for (var condition : conditions) {
            final var newPredicate      = conditionToPredicate(condition, objectMapper);
            final var previousPredicate = predicate;
            predicate = x -> previousPredicate.test(x) && newPredicate.test(x);
        }
        return mapPathNotFoundToAccessDeniedException(predicate);
    }

    private static void assertConstraintIsAnObjectNode(JsonNode constraint) {
        if (constraint == null || !constraint.isObject())
            throw new AccessConstraintViolationException(ERROR_CONSTRAINT_INVALID);

    }

    private static Predicate<Object> mapPathNotFoundToAccessDeniedException(Predicate<Object> predicate) {
        return x -> {
            try {
                return predicate.test(x);
            } catch (PathNotFoundException e) {
                throw new AccessConstraintViolationException(ERROR_CONSTRAINT_PATH_NOT_PRESENT, e);
            }
        };
    }

    private static Predicate<Object> conditionToPredicate(JsonNode condition, ObjectMapper objectMapper) {
        if (!condition.isObject())
            throw new AccessConstraintViolationException(ERROR_PREDICATE_CONDITION_INVALID + condition);

        if (!condition.has(PATH) || !condition.get(PATH).isString())
            throw new AccessConstraintViolationException(ERROR_PREDICATE_CONDITION_INVALID + condition);

        final var path = condition.get(PATH).stringValue();

        if (!condition.has(TYPE) || !condition.get(TYPE).isString())
            throw new AccessConstraintViolationException(ERROR_PREDICATE_CONDITION_INVALID + condition);

        final var type = condition.get(TYPE).stringValue();

        if (!condition.has(VALUE))
            throw new AccessConstraintViolationException(ERROR_PREDICATE_CONDITION_INVALID + condition);

        if (EQUALS.equals(type))
            return equalsCondition(condition, path, objectMapper);

        if (NEQ.equals(type))
            return Predicate.not(equalsCondition(condition, path, objectMapper));

        if (GEQ.equals(type))
            return geqCondition(condition, path, objectMapper);

        if (LEQ.equals(type))
            return leqCondition(condition, path, objectMapper);

        if (LT.equals(type))
            return ltCondition(condition, path, objectMapper);

        if (GT.equals(type))
            return gtCondition(condition, path, objectMapper);

        if (REGEX.equals(type))
            return regexCondition(condition, path, objectMapper);

        throw new AccessConstraintViolationException(ERROR_PREDICATE_CONDITION_INVALID + condition);
    }

    private static Predicate<Object> regexCondition(JsonNode condition, String path, ObjectMapper objectMapper) {
        if (!condition.get(VALUE).isString())
            throw new AccessConstraintViolationException(ERROR_PREDICATE_CONDITION_INVALID + condition);

        val patternText = condition.get(VALUE).stringValue();
        if (isDangerousRegex(patternText))
            throw new AccessConstraintViolationException(ERROR_REGEX_UNSAFE + patternText);

        final var regex = Pattern.compile(patternText);

        return original -> {
            final var value = getValueAtPath(original, path, objectMapper);
            if (!(value instanceof String stringValue))
                return false;
            return regex.asMatchPredicate().test(stringValue);
        };
    }

    private static Predicate<Object> leqCondition(JsonNode condition, String path, ObjectMapper objectMapper) {
        if (!condition.get(VALUE).isNumber())
            throw new AccessConstraintViolationException(ERROR_PREDICATE_CONDITION_INVALID + condition);

        final var conditionValue = condition.get(VALUE).asDouble();

        return original -> {
            final var value = getValueAtPath(original, path, objectMapper);
            if (!(value instanceof Number numberValue))
                return false;
            return numberValue.doubleValue() <= conditionValue;
        };
    }

    private static Predicate<Object> geqCondition(JsonNode condition, String path, ObjectMapper objectMapper) {
        if (!condition.get(VALUE).isNumber())
            throw new AccessConstraintViolationException(ERROR_PREDICATE_CONDITION_INVALID + condition);

        final var conditionValue = condition.get(VALUE).asDouble();

        return original -> {
            final var value = getValueAtPath(original, path, objectMapper);
            if (!(value instanceof Number numberValue))
                return false;
            return numberValue.doubleValue() >= conditionValue;
        };
    }

    private static Predicate<Object> ltCondition(JsonNode condition, String path, ObjectMapper objectMapper) {
        if (!condition.get(VALUE).isNumber())
            throw new AccessConstraintViolationException(ERROR_PREDICATE_CONDITION_INVALID + condition);

        final var conditionValue = condition.get(VALUE).asDouble();

        return original -> {
            final var value = getValueAtPath(original, path, objectMapper);
            if (!(value instanceof Number numberValue))
                return false;
            return numberValue.doubleValue() < conditionValue;
        };
    }

    private static Predicate<Object> gtCondition(JsonNode condition, String path, ObjectMapper objectMapper) {
        if (!condition.get(VALUE).isNumber())
            throw new AccessConstraintViolationException(ERROR_PREDICATE_CONDITION_INVALID + condition);

        final var conditionValue = condition.get(VALUE).asDouble();

        return original -> {
            final var value = getValueAtPath(original, path, objectMapper);
            if (!(value instanceof Number numberValue))
                return false;
            return numberValue.doubleValue() > conditionValue;
        };
    }

    private static Predicate<Object> numberEqCondition(JsonNode condition, String path, ObjectMapper objectMapper) {
        final var conditionValue = condition.get(VALUE).asDouble();

        return original -> {
            final var value = getValueAtPath(original, path, objectMapper);
            if (!(value instanceof Number numberValue))
                return false;
            return conditionValue == numberValue.doubleValue();
        };
    }

    private static Predicate<Object> equalsCondition(JsonNode condition, String path, ObjectMapper objectMapper) {
        final var valueNode = condition.get(VALUE);
        if (valueNode.isNumber())
            return numberEqCondition(condition, path, objectMapper);

        if (!valueNode.isString())
            throw new AccessConstraintViolationException(ERROR_PREDICATE_CONDITION_INVALID + condition);

        final var conditionValue = valueNode.stringValue();

        return original -> {
            final var value = getValueAtPath(original, path, objectMapper);
            if (!(value instanceof String stringValue))
                return false;
            return conditionValue.equals(stringValue);
        };
    }

    private static Object getValueAtPath(Object original, String path, ObjectMapper objectMapper) {
        // Convert to native Java types for jsonpath compatibility
        final var originalAsNative = objectMapper.convertValue(original, Object.class);
        final var jsonContext      = JsonPath.parse(originalAsNative);
        return jsonContext.read(path);
    }

    private static boolean noConditionsPresent(JsonNode constraint) {
        return !constraint.has(CONDITIONS);
    }

    private static void assertConditionsIsAnArrayNode(JsonNode constraint) {
        final var conditions = constraint.get(CONDITIONS);
        if (!conditions.isArray())
            throw new AccessConstraintViolationException(ERROR_CONDITIONS_NOT_AN_ARRAY + conditions);
    }

    public static UnaryOperator<Object> getTransformationHandler(JsonNode constraint, ObjectMapper objectMapper) {
        return original -> {
            final var actions = constraint.get(ACTIONS);
            if (actions == null)
                return original;

            if (!actions.isArray())
                throw new AccessConstraintViolationException(ERROR_ACTIONS_NOT_AN_ARRAY);

            // Convert to native Java types (Map/List) for jsonpath compatibility
            final var originalAsNative = objectMapper.convertValue(original, Object.class);
            final var jsonContext      = JsonPath.parse(originalAsNative);

            for (var action : actions)
                applyAction(jsonContext, action, objectMapper);

            Object modifiedNative = jsonContext.json();

            try {
                // Convert back to original type
                return objectMapper.convertValue(modifiedNative, original.getClass());
            } catch (IllegalArgumentException e) {
                throw new AccessConstraintViolationException(ERROR_CONVERTING_MODIFIED_OBJECT, e);
            }
        };
    }

    private static void applyAction(DocumentContext jsonContext, JsonNode action, ObjectMapper objectMapper) {
        if (!action.isObject())
            throw new AccessConstraintViolationException(ERROR_ACTION_NOT_AN_OBJECT);

        final var path       = getTextualValueOfActionKey(action, PATH);
        final var actionType = getTextualValueOfActionKey(action, TYPE).trim().toLowerCase();

        try {
            jsonContext.read(path);
        } catch (PathNotFoundException e) {
            throw new AccessConstraintViolationException(ERROR_CONSTRAINT_PATH_NOT_PRESENT_ENFORCEMENT, e);
        }

        switch (actionType) {
        case DELETE  -> {
            jsonContext.delete(path);
            return;
        }
        case BLACKEN -> {
            blacken(jsonContext, path, action);
            return;
        }
        case REPLACE -> {
            replace(jsonContext, path, action, objectMapper);
            return;
        }
        default      -> { /* no-op */ }
        }

        throw new AccessConstraintViolationException(String.format(ERROR_UNKNOWN_ACTION_S, actionType));

    }

    private static void replace(DocumentContext jsonContext, String path, JsonNode action, ObjectMapper objectMapper) {
        jsonContext.map(path, replaceNode(action, objectMapper));
    }

    private static MapFunction replaceNode(JsonNode action, ObjectMapper objectMapper) {
        return (original, configuration) -> {
            if (!action.has(REPLACEMENT))
                throw new AccessConstraintViolationException(ERROR_NO_REPLACEMENT_SPECIFIED);

            // Convert JsonNode replacement to native Java type
            return objectMapper.convertValue(action.get(REPLACEMENT), Object.class);
        };
    }

    private static void blacken(DocumentContext jsonContext, String path, JsonNode action) {
        jsonContext.map(path, blackenNode(action));
    }

    private static MapFunction blackenNode(JsonNode action) {
        return (original, configuration) -> {
            // With native Java types, original is now a String (not JsonNode)
            if (!(original instanceof String originalString))
                throw new AccessConstraintViolationException(ERROR_PATH_NOT_TEXTUAL);

            final var replacementString = determineReplacementString(action);
            final var discloseRight     = getIntegerValueOfActionKeyOrDefaultToZero(action, DISCLOSE_RIGHT);
            final var discloseLeft      = getIntegerValueOfActionKeyOrDefaultToZero(action, DISCLOSE_LEFT);
            final var blackenLength     = determineBlackenLength(action);

            return blackenUtil(originalString, replacementString, discloseRight, discloseLeft, blackenLength);
        };
    }

    private static int determineBlackenLength(JsonNode action) {
        final var replacementNode = action.get(LENGTH);

        if (replacementNode == null)
            return BLACKEN_LENGTH_INVALID_VALUE;

        if (replacementNode.isNumber() && replacementNode.intValue() >= 0)
            return replacementNode.intValue();

        throw new AccessConstraintViolationException(ERROR_LENGTH_NOT_NUMBER);
    }

    private static String blackenUtil(String originalString, String replacement, int discloseRight, int discloseLeft,
            int blackenLength) {
        if (discloseLeft + discloseRight >= originalString.length())
            return originalString;

        StringBuilder result = new StringBuilder();
        if (discloseLeft > 0) {
            result.append(originalString, 0, discloseLeft);
        }

        int replacedChars = originalString.length() - discloseLeft - discloseRight;

        int blackenFinalLength = (blackenLength == BLACKEN_LENGTH_INVALID_VALUE) ? replacedChars : blackenLength;

        result.append(String.valueOf(replacement).repeat(blackenFinalLength));
        if (discloseRight > 0) {
            result.append(originalString.substring(discloseLeft + replacedChars));
        }
        return result.toString();
    }

    private static String determineReplacementString(JsonNode action) {
        final var replacementNode = action.get(REPLACEMENT);

        if (replacementNode == null)
            return BLACK_SQUARE;

        if (replacementNode.isString())
            return replacementNode.stringValue();

        throw new AccessConstraintViolationException(ERROR_REPLACEMENT_NOT_TEXTUAL);
    }

    private static String getTextualValueOfActionKey(JsonNode action, String key) {
        final var value = getValueOfActionKey(action, key);

        if (!value.isString())
            throw new AccessConstraintViolationException(String.format(ERROR_VALUE_NOT_TEXTUAL_S, key));

        return value.stringValue();
    }

    private static int getIntegerValueOfActionKeyOrDefaultToZero(JsonNode action, String key) {
        if (!action.has(key))
            return 0;

        final var value = action.get(key);

        if (!value.canConvertToInt())
            throw new AccessConstraintViolationException(String.format(ERROR_VALUE_NOT_INTEGER_S, key));

        return value.intValue();
    }

    private static JsonNode getValueOfActionKey(JsonNode action, String key) {
        if (!action.hasNonNull(key))
            throw new AccessConstraintViolationException(String.format(ERROR_UNDEFINED_KEY_S, key));

        return action.get(key);
    }

    private static boolean isDangerousRegex(String pattern) {
        return REDOS_NESTED_QUANTIFIERS.matcher(pattern).find() || REDOS_ALTERNATION_WITH_QUANT.matcher(pattern).find()
                || REDOS_NESTED_WILDCARDS.matcher(pattern).find() || REDOS_NESTED_BOUNDED_QUANT.matcher(pattern).find()
                || pattern.contains(".*.*") || pattern.contains(".+.+");
    }
}
