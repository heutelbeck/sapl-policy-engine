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
package io.sapl.interpreter.validation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.util.Set;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Array;
import io.sapl.api.validation.Bool;
import io.sapl.api.validation.Int;
import io.sapl.api.validation.JsonObject;
import io.sapl.api.validation.Long;
import io.sapl.api.validation.Number;
import io.sapl.api.validation.Text;
import io.sapl.functions.SchemaValidationLibrary;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;

@UtilityClass
public class ParameterTypeValidator {

	private static final String ILLEGAL_PARAMETER_TYPE = "Illegal parameter type. Got: %s Expected: %s";

	private static final Set<Class<?>> VALIDATION_ANNOTATIONS = Set.of(Number.class, Int.class, Long.class, Bool.class,
			Text.class, Array.class, JsonObject.class);

	public static void validateType(Val parameterValue, Parameter parameterType) throws IllegalParameterType {
		if (!hasValidationAnnotations(parameterType))
			return;

		if (parameterValue.isError())
			throw new IllegalParameterType(
					String.format(ILLEGAL_PARAMETER_TYPE, "error", listAllowedTypes(parameterType.getAnnotations())));

		if (parameterValue.isUndefined())
			throw new IllegalParameterType(String.format(ILLEGAL_PARAMETER_TYPE, "undefined",
					listAllowedTypes(parameterType.getAnnotations())));

		validateJsonNodeType(parameterValue.get(), parameterType);
	}

	public static Flux<Val> validateType(Flux<Val> parameterFlux, Parameter parameterType) {
		if (!hasValidationAnnotations(parameterType))
			return parameterFlux;
		return parameterFlux.map(mapInvalidToError(parameterType));
	}

	private static Function<Val, Val> mapInvalidToError(Parameter parameterType) {
		return val -> {
			try {
				validateType(val, parameterType);
			} catch (IllegalParameterType e) {
				return Val.error(e);
			}
			return val;
		};
	}

	private static void validateJsonNodeType(JsonNode node, Parameter parameterType) throws IllegalParameterType {
		Annotation[] annotations = parameterType.getAnnotations();
		for (Annotation annotation : annotations)
			if (nodeContentsMatchesTypeGivenByAnnotation(node, annotation))
				return;

		throw new IllegalParameterType(
				String.format(ILLEGAL_PARAMETER_TYPE, node.getNodeType().toString(), listAllowedTypes(annotations)));
	}

	private static boolean nodeContentsMatchesTypeGivenByAnnotation(JsonNode node, Annotation annotation) {
		return (Number.class.isAssignableFrom(annotation.getClass()) && node.isNumber())
				|| (Int.class.isAssignableFrom(annotation.getClass()) && node.isNumber() && node.canConvertToInt())
				|| (Long.class.isAssignableFrom(annotation.getClass()) && node.isNumber() && node.canConvertToLong())
				|| (Bool.class.isAssignableFrom(annotation.getClass()) && node.isBoolean())
				|| (Text.class.isAssignableFrom(annotation.getClass()) && node.isTextual())
				|| (Array.class.isAssignableFrom(annotation.getClass()) && node.isArray())
				|| (JsonObject.class.isAssignableFrom(annotation.getClass()) && node.isObject()) && nodeCompliantWithSchema(node, annotation);
	}

	private static boolean nodeCompliantWithSchema(JsonNode node, Annotation annotation){
		String schema = ((JsonObject) annotation).schema();
		if (schema == null || schema.equals(""))
			return true;
		return SchemaValidationLibrary.isCompliantWithSchema(node, schema);
	}

	private static boolean hasValidationAnnotations(Parameter parameterType) {
		for (var annotation : parameterType.getAnnotations())
			if (isTypeValidationAnnotation(annotation))
				return true;

		return false;
	}

	private static boolean isTypeValidationAnnotation(Annotation annotation) {
		for (var validator : VALIDATION_ANNOTATIONS)
			if (validator.isAssignableFrom(annotation.getClass()))
				return true;

		return false;
	}

	private static String listAllowedTypes(Annotation[] annotations) {
		var builder = new StringBuilder();
		for (var annotation : annotations) {
			if (isTypeValidationAnnotation(annotation))
				builder.append(annotation).append(' ');
		}
		return builder.toString();
	}

}
