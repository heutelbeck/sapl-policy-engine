/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.interpreter.validation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Array;
import io.sapl.api.validation.Bool;
import io.sapl.api.validation.Int;
import io.sapl.api.validation.JsonObject;
import io.sapl.api.validation.Long;
import io.sapl.api.validation.Number;
import io.sapl.api.validation.Text;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ParameterTypeValidator {

	private static final String ILLEGAL_PARAMETER_TYPE = "Illegal parameter type. Was: %s";

	public static void validateType(Val val, Parameter type) throws IllegalParameterType {
		if (val.isUndefined()) {
			throw new IllegalParameterType(String.format(ILLEGAL_PARAMETER_TYPE, "undefined"));
		}
		validateType(val.get(), type);
	}

	public static void validateType(JsonNode node, Parameter type) throws IllegalParameterType {
		Annotation[] annotations = type.getAnnotations();
		boolean valid = annotations.length == 0;
		for (Annotation annotation : annotations) {
			// valid if any annotation matches
			if ((Number.class.isAssignableFrom(annotation.getClass()) && node.isNumber())
					|| (Int.class.isAssignableFrom(annotation.getClass()) && node.isNumber() && node.canConvertToInt())
					|| (Long.class.isAssignableFrom(annotation.getClass()) && node.isNumber()
							&& node.canConvertToLong())
					|| (Bool.class.isAssignableFrom(annotation.getClass()) && node.isBoolean()
							&& node.canConvertToInt())
					|| (Text.class.isAssignableFrom(annotation.getClass()) && node.isTextual())
					|| (Array.class.isAssignableFrom(annotation.getClass()) && node.isArray())
					|| (JsonObject.class.isAssignableFrom(annotation.getClass()) && node.isObject())) {
				valid = true;
			}
		}
		if (!valid) {
			throw new IllegalParameterType(String.format(ILLEGAL_PARAMETER_TYPE, node.getNodeType().toString()));
		}
	}

}
