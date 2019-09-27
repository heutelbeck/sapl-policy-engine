package io.sapl.interpreter.validation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;

import com.fasterxml.jackson.databind.JsonNode;
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

	private static final String ILLEGAL_PARAMETER_TYPE = "Illegal parameter type.";

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
			throw new IllegalParameterType(ILLEGAL_PARAMETER_TYPE);
		}
	}

}
