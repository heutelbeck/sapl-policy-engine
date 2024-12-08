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
package io.sapl.springdatacommon.queries;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import lombok.experimental.UtilityClass;

/**
 * Utility class to resolve bounded method parameters and annotation parameters
 * based on the database type.
 */
@UtilityClass
public class QueryAnnotationParameterResolver {

    /**
     * Resolves bounded method parameters and annotation parameters for a relational
     * database.
     *
     * @param method The method to resolve parameters for.
     * @param args The arguments passed to the method.
     * @return The resolved query with substituted parameters.
     */
    public static String resolveForRelationalDatabase(Method method, Object[] args) {

        final var queryAnnotation = method.getAnnotation(org.springframework.data.r2dbc.repository.Query.class);
        final var query           = queryAnnotation.value();
        final var parameterNames  = getParameterNames(method);
        final var finalArgs       = convertArgumentsToString(args);

        return replaceParametersRelational(query, parameterNames, finalArgs);
    }

    /**
     * Resolves bounded method parameters and annotation parameters for MongoDB.
     *
     * @param method The method to resolve parameters for.
     * @param args The arguments passed to the method.
     * @return The resolved query with substituted parameters.
     */
    public static String resolveForMongoDB(Method method, Object[] args) {

        final var queryAnnotation = method.getAnnotation(org.springframework.data.mongodb.repository.Query.class);
        final var query           = String.format("%sXXXXX%sXXXXX%s", queryAnnotation.value(), queryAnnotation.fields(),
                queryAnnotation.sort());
        final var parameterNames  = getParameterNames(method);
        final var finalArgs       = convertArgumentsToString(args);

        return replaceParameters(query, parameterNames, finalArgs);
    }

    private static List<String> getParameterNames(Method method) {
        return Arrays.stream(method.getParameters()).map(Parameter::getName).toList();
    }

    private static String replaceParameters(String query, Collection<String> parameterNames, List<String> arguments) {
        for (int i = 0; i < parameterNames.size(); i++) {
            query = query.replace("?" + i, arguments.get(i));
        }
        return query;
    }

    private static String replaceParametersRelational(String query, List<String> parameterNames,
            List<String> arguments) {
        for (int i = 0; i < parameterNames.size(); i++) {
            query = query.replace("(:" + parameterNames.get(i) + ")", arguments.get(i));
        }
        return query;
    }

    private static List<String> convertArgumentsToString(Object[] args) {
        return Arrays.stream(args).map(arg -> arg instanceof String ? "'" + arg + "'" : arg.toString()).toList();
    }
}
