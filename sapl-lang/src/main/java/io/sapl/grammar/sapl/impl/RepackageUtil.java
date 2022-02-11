/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.grammar.sapl.impl;

import io.sapl.api.interpreter.Val;
import lombok.experimental.UtilityClass;
import reactor.util.function.Tuple2;

@UtilityClass
public class RepackageUtil {

	public Val recombineObject(Object[] oElements) {
		var object = Val.JSON.objectNode();
		for (var elem : oElements) {
			@SuppressWarnings("unchecked")
			var element = (Tuple2<String, Val>) elem;
			if (element.getT2().isError()) {
				return element.getT2();
			}
			// drop undefined
			if (element.getT2().isDefined()) {
				object.set(element.getT1(), element.getT2().get());
			}
		}
		return Val.of(object);
	}

	public Val recombineArray(Object[] oElements) {
		var array = Val.JSON.arrayNode();
		for (var elem : oElements) {
			var element = (Val) elem;
			if (element.isError()) {
				return element;
			}
			// drop undefined
			if (element.isDefined()) {
				array.add(element.get());
			}
		}
		return Val.of(array);
	}

}
