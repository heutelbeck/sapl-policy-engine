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

import java.util.function.BiPredicate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Array;
import io.sapl.api.validation.JsonObject;
import io.sapl.api.validation.Text;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

@UtilityClass
@FunctionLibrary(name = StandardFunctionLibrary.NAME, description = StandardFunctionLibrary.DESCRIPTION)
public class StandardFunctionLibrary {

    public static final String NAME        = "standard";
    public static final String DESCRIPTION = "This the standard function library for SAPL.";

    private static final XmlMapper XML_MAPPER = new XmlMapper();

    private static final String RETURNS_ARRAY = """
            {
                "type": "array"
            }
            """;

    @Function(docs = """
            ```concatenate(ARRAY...arrays)```: Creates a new array concatenating the all array parameters in ```...arrays```.
            It keepts the order of array parameters and the inner order of the arrays as provided.

            Example:
            ```
            import standard.*
            policy "example"
            permit
            where
              concatenate([1, 2, 3, 4], [3, 4, 5, 6]) == [1, 2, 3, 4, 3, 4, 5, 6];
            ```
            """, schema = RETURNS_ARRAY)
    public static Val concatenate(@Array Val... arrays) {
        final var newArray = Val.JSON.arrayNode();
        for (var array : arrays) {
            final var jsonArray        = array.getArrayNode();
            final var elementsIterator = jsonArray.elements();
            while (elementsIterator.hasNext()) {
                newArray.add(elementsIterator.next().deepCopy());
            }
        }
        return Val.of(newArray);
    }

    @Function(docs = """
            ```difference(ARRAY array1, ARRAY array2)```: Returns the difference between the ```array1``` and ```array2```,
            removing duplicates.
            Attention: numerically equivalent but differently written, i.e., ```0``` vs ```0.000```, numbers may be
            interpreted as non-eqivalent.

            Example:
            ```
            import standard.*
            policy "example"
            permit
            where
              difference([1, 2, 3, 4], [3, 4, 5, 6]) == [1, 2, 5, 6];
            ```
            """, schema = RETURNS_ARRAY)
    public static Val difference(@Array Val array1, @Array Val array2) {
        final var newArray         = Val.JSON.arrayNode();
        final var jsonArray        = array1.getArrayNode();
        final var elementsIterator = jsonArray.elements();
        while (elementsIterator.hasNext()) {
            final var nextElement = elementsIterator.next();
            if (!contains(nextElement, array2.getArrayNode(), Object::equals)
                    && !contains(nextElement, newArray, Object::equals)) {
                newArray.add(nextElement.deepCopy());
            }
        }
        return Val.of(newArray);
    }

    @Function(docs = """
            ```union(ARRAY...arrays)```: Creates a copy of the array parameters in ```...arrays``` containing all elements
            of the provided arrays, but removing all duplicate elements.
            Attention: numerically equivalent but differently written, i.e., ```0``` vs ```0.000```, numbers may be
            interpreted as non-eqivalent.

            Example:
            ```
            import standard.*
            policy "example"
            permit
            where
              union([1, 2, 3, 4], [3, 4, 5, 6]) == [1, 2, 3, 4, 5, 6];
            ```
            """, schema = RETURNS_ARRAY)
    public static Val union(@Array Val... arrays) {
        final var newArray = Val.JSON.arrayNode();
        for (var array : arrays) {
            final var jsonArray        = array.getArrayNode();
            final var elementsIterator = jsonArray.elements();
            while (elementsIterator.hasNext()) {
                final var nextElement = elementsIterator.next();
                if (!contains(nextElement, newArray, Object::equals)) {
                    newArray.add(nextElement.deepCopy());
                }
            }
        }
        return Val.of(newArray);
    }

    @Function(docs = """
            ```toSet(ARRAY array)```: Creates a copy of the ```array``` preserving the original order, but removing all
            duplicate elements.
            Attention: numerically equivalent but differently written, i.e., ```0``` vs ```0.000```, numbers may be
            interpreted as non-eqivalent.

            Example:
            ```
            import standard.*
            policy "example"
            permit
            where
              toSet([1, 2, 3, 4, 3, 2, 1]) == [1, 2, 3, 4];
            ```
            """, schema = RETURNS_ARRAY)
    public static Val toSet(@Array Val array) {
        final var newArray         = Val.JSON.arrayNode();
        final var jsonArray        = array.getArrayNode();
        final var elementsIterator = jsonArray.elements();
        while (elementsIterator.hasNext()) {
            final var nextElement = elementsIterator.next();
            if (!contains(nextElement, newArray, Object::equals)) {
                newArray.add(nextElement.deepCopy());
            }
        }
        return Val.of(newArray);
    }

    @Function(docs = """
            ```intersect(ARRAY...arrays)```: Creates a new array only containing elements present in all
            parameter arrays from ```...arrays```, while removing all duplicate elements.
            Attention: numerically equivalent but differently written, i.e., ```0``` vs ```0.000```, numbers may be
            interpreted as non-eqivalent.

            Example:
            ```
            import standard.*
            policy "example"
            permit
            where
              intersect([1, 2, 3, 4], [3, 4, 5, 6]) == [3, 4];
            ```
            """, schema = RETURNS_ARRAY)
    public static Val intersect(@Array Val... arrays) {
        return intersect(arrays, Object::equals);
    }

    private static Val intersect(Val[] arrays, BiPredicate<JsonNode, JsonNode> equalityValidator) {
        if (arrays.length == 0) {
            return Val.ofEmptyArray();
        }

        var intersection = Val.of(arrays[0].getArrayNode().deepCopy());
        for (var i = 1; i < arrays.length; i++) {
            intersection = intersect(intersection, arrays[i], equalityValidator);
        }
        return intersection;
    }

    private static Val intersect(Val array1, Val array2, BiPredicate<JsonNode, JsonNode> equalityValidator) {
        final var newArray         = Val.JSON.arrayNode();
        final var jsonArray        = array1.getArrayNode();
        final var elementsIterator = jsonArray.elements();
        while (elementsIterator.hasNext()) {
            final var nextElement = elementsIterator.next();
            if (contains(nextElement, array2.getArrayNode(), equalityValidator)) {
                newArray.add(nextElement.deepCopy());
            }
        }
        return Val.of(newArray);
    }

    private static boolean contains(JsonNode element, ArrayNode array,
            BiPredicate<JsonNode, JsonNode> equalityValidator) {
        final var elementsIterator = array.elements();
        while (elementsIterator.hasNext()) {
            final var nextElement = elementsIterator.next();
            if (equalityValidator.test(element, nextElement)) {
                return true;
            }
        }
        return false;
    }

    @Function(docs = """
            ```length(ARRAY|TEXT|JSON value)```: For TEXT it returns the length of the text string.
            For ARRAY, it returns the number of elements in the array.
            For OBJECT, it returns the number of keys in the OBJECT.
            For NUMBER, BOOLEAN, or NULL, the function will return an error.

            Example:
            ```
            import standard.*
            policy "example"
            permit
            where
              length([1, 2, 3, 4]) == 4;
              length("example") == 7;
              length({ "key1" : 1, "key2" : 2}) == 2;
            ```
            """, schema = """
            { "type": "integer" }""")
    public static Val length(@Array @Text @JsonObject Val value) {
        if (value.isTextual()) {
            return Val.of(value.getText().length());
        }
        return Val.of(value.get().size());
    }

    @Function(name = "toString", docs = """
            ```toString(value)```: Converts any ```value``` to a string representation.

            Example:
            ```
            import standard.*
            policy "example"
            permit
            where
              toString([1,2,3]) == "[1,2,3]";
            ```
            """, schema = """
            { "type": "string" }""")
    public static Val asString(Val value) {
        if (value.isTextual()) {
            return Val.of(value.get().asText());
        }
        return Val.of(value.toString());
    }

    @Function(docs = """
            ```onErrorMap(guardedExpression, fallbackExpression)```: If ```guardedExpression``` is an error,
            the ```fallback``` is returned instead.

            Example:
            ```
            import standard.*
            policy "example"
            permit
            where
              onErrorMap(1/0,999) == 999;
            ```
            """)
    public static Val onErrorMap(Val guardedExpression, Val fallback) {
        if (guardedExpression.isError()) {
            return fallback;
        }
        return guardedExpression;
    }

    @SneakyThrows
    @Function(docs = """
            ```xmlToVal(TEXT xml)```: Converts a well-formed XML document ```xml``` into a SAPL
            value representing the content of the XML document.

            Example:
            ```
            import standard.*
            policy "example"
            permit
            where
               var xml = "<Flower><name>Poppy</name><color>RED</color><petals>9</petals></Flower>";
               xmlToVal(xml) == {"name":"Poppy","color":"RED","petals":"9"};
            ```
            """)
    public Val xmlToVal(@Text Val xml) {
        return Val.of(XML_MAPPER.readTree(xml.getText()));
    }

    @SneakyThrows
    @Function(docs = """
            ```jsonToVal(TEXT json)```: Converts a well-formed JSON document ```json``` into a SAPL
            value representing the content of the JSON document.

            Example:
            ```
            import standard.*
            policy "example"
            permit
            where
               var json = "{ \"hello\": \"world\" }";
               jsonToVal(json) == { "hello":"world" };
            ```
            """)
    public Val jsonToVal(@Text Val json) {
        return Val.ofJson(json.getText());
    }

}
