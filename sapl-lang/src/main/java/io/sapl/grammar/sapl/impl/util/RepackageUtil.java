/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.grammar.sapl.impl.util;

import io.sapl.api.interpreter.ExpressionArgument;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.Array;
import lombok.experimental.UtilityClass;
import reactor.util.function.Tuple2;

@UtilityClass
public class RepackageUtil {

    public Val recombineObject(Object[] oElements) {
        var object         = Val.JSON.objectNode();
        var tracedElements = new ExpressionArgument[oElements.length];
        var elementCount   = 0;
        Val error          = null;
        for (var elem : oElements) {
            @SuppressWarnings("unchecked")
            var element = (Tuple2<String, Val>) elem;
            var key     = element.getT1();
            var value   = element.getT2();
            tracedElements[elementCount++] = new ExpressionArgument(key, value);
            if (value.isError() && error == null) {
                error = value;
            } else if (value.isDefined()) { // drop undefined
                object.set(element.getT1(), element.getT2().get());
            }
        }
        if (error != null)
            return error.withTrace(Object.class, tracedElements);
        return Val.of(object).withTrace(Object.class, tracedElements);
    }

    public Val recombineArray(Object[] oElements) {
        var array          = Val.JSON.arrayNode();
        var tracedElements = new ExpressionArgument[oElements.length];
        var elementCount   = 0;
        Val error          = null;
        for (var elem : oElements) {
            var element = (Val) elem;
            tracedElements[elementCount] = new ExpressionArgument("array[" + elementCount + "]", element);
            elementCount++;
            if (element.isError() && error == null) {
                error = element;
            }
            // drop undefined
            if (element.isDefined()) {
                array.add(element.get());
            }
        }
        if (error != null)
            return error.withTrace(Array.class, tracedElements);
        return Val.of(array).withTrace(Array.class, tracedElements);
    }

}
