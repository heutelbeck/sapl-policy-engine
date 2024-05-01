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
package io.sapl.functions;

import java.util.function.BiFunction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Array;
import io.sapl.api.validation.Bool;
import io.sapl.api.validation.JsonObject;
import io.sapl.api.validation.Number;
import io.sapl.api.validation.Text;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

@UtilityClass
@FunctionLibrary(name = StandardFunctionLibrary.NAME, description = StandardFunctionLibrary.DESCRIPTION)
public class StandardFunctionLibrary {

    public static final String NAME        = "standard";
    public static final String DESCRIPTION = "This library contains the mandatory functions for the SAPL implementation.";

    private static final String ON_ERROR_MAP_DOC = "onErrorMap(guardedExpression, fallbackExpression): If the guarded expression evaluates to an error, return the evaluation result of the fallbackExpression.";

    private static final String LENGTH_DOC = "length(JSON_VALUE): For STRING it returns the length of the STRING. "
            + "For ARRAY, it returns the number of elements in the array. "
            + "For OBJECT, it returns the number of keys in the OBJECT. "
            + "For NUMBER, BOOLEAN, or NULL, the function will return an error.";

    private static final String NUMBER_TO_STRING_DOC = "numberToString(JSON_VALUE): For STRING it returns the input. "
            + "For NUMBER or BOOLEAN it returns a JSON node representing the value converted to a string. "
            + "For NULL it returns a JSON node representing the empty string. "
            + "For ARRAY or OBJECT the function will return an error.";

    private static final String CONCATENATE_DOC = "concatenate(ARRAY...arrays): Creates a new array concatenating the parameter arrays.";

    private static final String INTERSECT_DOC = """
            intersect(ARRAY...arrays): Creates a new array only containing elements present in all parameter arrays, but removing all duplicate elements. \
            Attention: numerically equivalent but differently written, i.e., 0 vs 0.000, numbers may be interpreted as non-eqivalent.
            """;

    private static final String UNION_DOC = """
            toSet(ARRAY..arrays): Creates a copy of the arrays containing all elements of the provided arrays, but removing all duplicate elements. \
            Attention: numerically equivalent but differently written, i.e., 0 vs 0.000, numbers may be interpreted as non-eqivalent.
            """;

    private static final String TO_SET_DOC = """
            toSet(ARRAY): Creates a copy of the array preserving the original order, but removing all duplicate elements. \
            Attention: numerically equivalent but differently written, i.e., 0 vs 0.000, numbers may be interpreted as non-eqivalent.
            """;

    private static final String DIFFERENCE_DOC = """
            difference(ARRAY,ARRAY): Returns the difference between the first and the second array, removing duplicates. \
            Attention: numerically equivalent but differently written, i.e., 0 vs 0.000, numbers may be interpreted as non-eqivalent.
            """;

    public static final String XML_TO_JSON_DOC = "xmlToJson(TEXT) Converts XML to JSON";

    private static final XmlMapper XML_MAPPER = new XmlMapper();

    @Function(docs = CONCATENATE_DOC)
    public static Val concatenate(@Array Val... arrays) {
        var newArray = Val.JSON.arrayNode();
        for (var array : arrays) {
            var jsonArray        = array.getArrayNode();
            var elementsIterator = jsonArray.elements();
            while (elementsIterator.hasNext()) {
                newArray.add(elementsIterator.next().deepCopy());
            }
        }
        return Val.of(newArray);
    }

    @Function(docs = DIFFERENCE_DOC)
    public static Val difference(@Array Val array1, @Array Val array2) {
        var newArray         = Val.JSON.arrayNode();
        var jsonArray        = array1.getArrayNode();
        var elementsIterator = jsonArray.elements();
        while (elementsIterator.hasNext()) {
            var nextElement = elementsIterator.next();
            if (!contains(nextElement, array2.getArrayNode(), (a, b) -> a.equals(b))
                    && !contains(nextElement, newArray, (a, b) -> a.equals(b))) {
                newArray.add(nextElement.deepCopy());
            }
        }
        return Val.of(newArray);
    }

    @Function(docs = UNION_DOC)
    public static Val union(@Array Val... arrays) {
        var newArray = Val.JSON.arrayNode();
        for (var array : arrays) {
            var jsonArray        = array.getArrayNode();
            var elementsIterator = jsonArray.elements();
            while (elementsIterator.hasNext()) {
                var nextElement = elementsIterator.next();
                if (!contains(nextElement, newArray, (a, b) -> a.equals(b))) {
                    newArray.add(nextElement.deepCopy());
                }
            }
        }
        return Val.of(newArray);
    }

    @Function(docs = TO_SET_DOC)
    public static Val toSet(@Array Val array) {
        var newArray         = Val.JSON.arrayNode();
        var jsonArray        = array.getArrayNode();
        var elementsIterator = jsonArray.elements();
        while (elementsIterator.hasNext()) {
            var nextElement = elementsIterator.next();
            if (!contains(nextElement, newArray, (a, b) -> a.equals(b))) {
                newArray.add(nextElement.deepCopy());
            }
        }
        return Val.of(newArray);
    }

    @Function(docs = INTERSECT_DOC)
    public static Val intersect(@Array Val... arrays) {
        return intersect(arrays, (a, b) -> a.equals(b));
    }

    private static Val intersect(Val[] arrays, BiFunction<JsonNode, JsonNode, Boolean> equalityValidator) {
        if (arrays.length == 0) {
            return Val.ofEmptyArray();
        }

        var intersection = Val.of(arrays[0].getArrayNode().deepCopy());
        for (var i = 1; i < arrays.length; i++) {
            intersection = intersect(intersection, arrays[i], equalityValidator);
        }
        return intersection;
    }

    private static Val intersect(Val array1, Val array2, BiFunction<JsonNode, JsonNode, Boolean> equalityValidator) {
        var newArray         = Val.JSON.arrayNode();
        var jsonArray        = array1.getArrayNode();
        var elementsIterator = jsonArray.elements();
        while (elementsIterator.hasNext()) {
            var nextElement = elementsIterator.next();
            if (contains(nextElement, array2.getArrayNode(), equalityValidator)) {
                newArray.add(nextElement.deepCopy());
            }
        }
        return Val.of(newArray);
    }

    private static boolean contains(JsonNode element, ArrayNode array,
            BiFunction<JsonNode, JsonNode, Boolean> equalityValidator) {
        var elementsIterator = array.elements();
        while (elementsIterator.hasNext()) {
            var nextElement = elementsIterator.next();
            if (equalityValidator.apply(element, nextElement)) {
                return true;
            }
        }
        return false;
    }

    @Function(docs = LENGTH_DOC)
    public static Val length(@Array @Text @JsonObject Val parameter) {
        if (parameter.isTextual())
            return Val.of(parameter.getText().length());

        return Val.of(parameter.get().size());
    }

    @Function(docs = NUMBER_TO_STRING_DOC)
    public static Val numberToString(@Text @Number @Bool Val parameter) {
        JsonNode param = parameter.get();
        if (param.isNumber())
            return Val.of(param.numberValue().toString());

        if (param.isBoolean())
            return Val.of(String.valueOf(param.booleanValue()));

        if (param.isNull())
            return Val.of("");

        return parameter;
    }

    @Function(docs = ON_ERROR_MAP_DOC)
    public static Val onErrorMap(Val guardedExpression, Val fallbackValue) {
        if (guardedExpression.isError())
            return fallbackValue;

        return guardedExpression;
    }

    @SneakyThrows
    @Function(docs = XML_TO_JSON_DOC)
    public Val xmlToJson(@Text Val xml) {
        return Val.of(XML_MAPPER.readTree(xml.getText()));
    }
}
