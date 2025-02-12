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
package io.sapl.testutil;

import java.io.Serializable;
import java.util.Comparator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NumericNode;

import io.sapl.api.SaplVersion;
import io.sapl.api.interpreter.Val;

/**
 * Test Utility class with methods to manage number arrays.
 */
public class ArrayUtil {

    private static final NumericAwareComparator EQ = new NumericAwareComparator();

    public static Val numberArray(Integer... values) {
        final var array = Val.JSON.arrayNode();
        for (var val : values) {
            array.add(val);
        }
        return Val.of(array);
    }

    public static Val numberArrayRange(int from, int to) {
        final var array = Val.JSON.arrayNode();
        if (from < to) {
            for (int i = from; i <= to; i++) {
                array.add(i);
            }
        } else {
            for (int i = from; i >= to; i--) {
                array.add(i);
            }
        }
        return Val.of(array);
    }

    public static boolean arraysMatchWithSetSemantics(Val result, Val expected) {
        if (result.getArrayNode().size() != expected.getArrayNode().size())
            return false;
        final var iter = expected.getArrayNode().elements();
        while (iter.hasNext()) {
            final var element = iter.next();
            if (!containsElement(result.getArrayNode(), element))
                return false;
        }
        return true;
    }

    private static boolean containsElement(ArrayNode arrayNode, JsonNode element) {
        final var iter = arrayNode.elements();
        while (iter.hasNext()) {
            final var arrayElement = iter.next();
            if (element.equals(EQ, arrayElement))
                return true;
        }
        return false;
    }

    private static class NumericAwareComparator implements Comparator<JsonNode>, Serializable {

        private static final long serialVersionUID = SaplVersion.VERISION_UID;

        @Override
        public int compare(JsonNode o1, JsonNode o2) {
            if (o1.equals(o2)) {
                return 0;
            }
            if ((o1 instanceof NumericNode) && (o2 instanceof NumericNode)) {
                return o1.decimalValue().compareTo(o2.decimalValue());
            }
            return 1;
        }
    }
}
