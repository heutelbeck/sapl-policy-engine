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
package io.sapl.documentation;

import io.sapl.api.documentation.EntryDocumentation;
import io.sapl.api.documentation.EntryType;
import io.sapl.api.documentation.LibraryDocumentation;
import io.sapl.api.documentation.LibraryType;
import io.sapl.api.documentation.ParameterDocumentation;
import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.EnvironmentAttribute;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.api.validation.Array;
import io.sapl.api.validation.Bool;
import io.sapl.api.validation.Int;
import io.sapl.api.validation.JsonObject;
import io.sapl.api.validation.Number;
import io.sapl.api.validation.Schema;
import io.sapl.api.validation.Text;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Extracts documentation from annotated SAPL extension library classes.
 * <p>
 * This utility processes {@link FunctionLibrary} and
 * {@link PolicyInformationPoint} annotated classes, extracting
 * documentation metadata into serializable DTOs.
 */
@UtilityClass
public class LibraryDocumentationExtractor {

    private static final Class<?>[] TYPE_ANNOTATIONS = { Text.class, Number.class, Int.class,
            io.sapl.api.validation.Long.class, Bool.class, Array.class, JsonObject.class };

    private static final Map<Class<?>, String> TYPE_NAMES = Map.of(Text.class, "Text", Number.class, "Number",
            Int.class, "Int", io.sapl.api.validation.Long.class, "Long", Bool.class, "Bool", Array.class, "Array",
            JsonObject.class, "Object");

    /**
     * Extracts documentation from a function library class.
     *
     * @param libraryClass
     * the class annotated with {@link FunctionLibrary}
     *
     * @return the extracted library documentation
     *
     * @throws IllegalArgumentException
     * if the class is not annotated with {@link FunctionLibrary}
     */
    public static LibraryDocumentation extractFunctionLibrary(Class<?> libraryClass) {
        val annotation = libraryClass.getAnnotation(FunctionLibrary.class);
        if (annotation == null) {
            throw new IllegalArgumentException(
                    "Class %s is not annotated with @FunctionLibrary".formatted(libraryClass.getName()));
        }

        val name    = annotation.name().isEmpty() ? libraryClass.getSimpleName() : annotation.name();
        val entries = new ArrayList<EntryDocumentation>();

        for (Method method : libraryClass.getDeclaredMethods()) {
            val functionAnnotation = method.getAnnotation(Function.class);
            if (functionAnnotation != null) {
                entries.add(extractFunctionEntry(method, functionAnnotation));
            }
        }

        return new LibraryDocumentation(LibraryType.FUNCTION_LIBRARY, name, annotation.description(),
                annotation.libraryDocumentation(), List.copyOf(entries));
    }

    /**
     * Extracts documentation from a Policy Information Point class.
     *
     * @param pipClass
     * the class annotated with {@link PolicyInformationPoint}
     *
     * @return the extracted library documentation
     *
     * @throws IllegalArgumentException
     * if the class is not annotated with {@link PolicyInformationPoint}
     */
    public static LibraryDocumentation extractPolicyInformationPoint(Class<?> pipClass) {
        val annotation = pipClass.getAnnotation(PolicyInformationPoint.class);
        if (annotation == null) {
            throw new IllegalArgumentException(
                    "Class %s is not annotated with @PolicyInformationPoint".formatted(pipClass.getName()));
        }

        val name    = annotation.name().isEmpty() ? pipClass.getSimpleName() : annotation.name();
        val entries = new ArrayList<EntryDocumentation>();

        for (Method method : pipClass.getDeclaredMethods()) {
            val attributeAnnotation = method.getAnnotation(Attribute.class);
            if (attributeAnnotation != null) {
                entries.add(extractAttributeEntry(method, attributeAnnotation, false));
                continue;
            }

            val envAttributeAnnotation = method.getAnnotation(EnvironmentAttribute.class);
            if (envAttributeAnnotation != null) {
                entries.add(extractEnvironmentAttributeEntry(method, envAttributeAnnotation));
            }
        }

        return new LibraryDocumentation(LibraryType.POLICY_INFORMATION_POINT, name, annotation.description(),
                annotation.pipDocumentation(), List.copyOf(entries));
    }

    private static EntryDocumentation extractFunctionEntry(Method method, Function annotation) {
        val name       = annotation.name().isEmpty() ? method.getName() : annotation.name();
        val schema     = loadSchema(method, annotation.schema(), annotation.pathToSchema());
        val parameters = extractParameters(method.getParameters(), 0);

        return new EntryDocumentation(EntryType.FUNCTION, name, annotation.docs(), schema, parameters);
    }

    private static EntryDocumentation extractAttributeEntry(Method method, Attribute annotation,
            boolean isEnvironment) {
        val name       = annotation.name().isEmpty() ? method.getName() : annotation.name();
        val schema     = loadSchema(method, annotation.schema(), annotation.pathToSchema());
        val startIndex = determineParameterStartIndex(method, isEnvironment);
        val parameters = extractParameters(method.getParameters(), startIndex);

        return new EntryDocumentation(EntryType.ATTRIBUTE, name, annotation.docs(), schema, parameters);
    }

    private static EntryDocumentation extractEnvironmentAttributeEntry(Method method, EnvironmentAttribute annotation) {
        val name       = annotation.name().isEmpty() ? method.getName() : annotation.name();
        val schema     = loadSchema(method, annotation.schema(), annotation.pathToSchema());
        val startIndex = determineParameterStartIndex(method, true);
        val parameters = extractParameters(method.getParameters(), startIndex);

        return new EntryDocumentation(EntryType.ENVIRONMENT_ATTRIBUTE, name, annotation.docs(), schema, parameters);
    }

    private static int determineParameterStartIndex(Method method, boolean isEnvironmentAttribute) {
        val parameters = method.getParameters();
        if (parameters.length == 0) {
            return 0;
        }

        int index = 0;

        // Entity parameter for non-environment attributes
        if (!isEnvironmentAttribute && parameters.length > 0) {
            val firstType = parameters[0].getType().getSimpleName();
            if ("Value".equals(firstType) || firstType.endsWith("Value")) {
                index++;
            }
        }

        // Variables map parameter
        if (index < parameters.length && Map.class.equals(parameters[index].getType())) {
            index++;
        }

        return index;
    }

    private static List<ParameterDocumentation> extractParameters(Parameter[] parameters, int startIndex) {
        val result = new ArrayList<ParameterDocumentation>();

        for (int i = startIndex; i < parameters.length; i++) {
            val parameter = parameters[i];
            val isLast    = i == parameters.length - 1;
            val isVarArgs = isLast && parameter.getType().isArray();

            val allowedTypes    = extractAllowedTypes(parameter);
            val parameterSchema = extractParameterSchema(parameter);

            result.add(new ParameterDocumentation(parameter.getName(), allowedTypes, isVarArgs, parameterSchema));
        }

        return List.copyOf(result);
    }

    private static List<String> extractAllowedTypes(Parameter parameter) {
        val types = new ArrayList<String>();

        for (Class<?> annotationType : TYPE_ANNOTATIONS) {
            if (parameter.isAnnotationPresent((Class<? extends Annotation>) annotationType)) {
                types.add(TYPE_NAMES.get(annotationType));
            }
        }

        return List.copyOf(types);
    }

    private static String extractParameterSchema(Parameter parameter) {
        val schemaAnnotation = parameter.getAnnotation(Schema.class);
        if (schemaAnnotation != null && !schemaAnnotation.value().isEmpty()) {
            return schemaAnnotation.value();
        }
        return null;
    }

    private static String loadSchema(Method method, String schemaString, String pathToSchema) {
        if (!schemaString.isEmpty()) {
            return schemaString;
        }

        if (!pathToSchema.isEmpty()) {
            return loadSchemaFromResource(method, pathToSchema);
        }

        return null;
    }

    private static String loadSchemaFromResource(Method method, String resourcePath) {
        try (var inputStream = method.getDeclaringClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                return null;
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

}
