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
package io.sapl.spring.data.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

@UtilityClass
public class Utilities {
    public static final String R2DBC_QUERY_MANIPULATION = "r2dbcQueryManipulation";
    public static final String MONGO_QUERY_MANIPULATION = "mongoQueryManipulation";
    public static final String TRANSFORMATIONS          = "transformations";
    public static final String CONDITIONS               = "conditions";
    public static final String SELECTION                = "selection";
    public static final String WHITELIST                = "whitelist";
    public static final String BLACKLIST                = "blacklist";
    public static final String COLUMNS                  = "columns";
    public static final String ALIAS                    = "alias";
    public static final String TYPE                     = "type";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Pattern PREFIX_TEMPLATE = Pattern
            .compile("^(find|read|get|query|search|stream)(All)?By[A-Z].*");

    private static final Pattern DEFAULT_METHODS = Pattern.compile("^(find|read|get|query|search|stream)All$");

    public boolean isMethodNameValid(String methodName) {
        final var matcher = PREFIX_TEMPLATE.matcher(methodName);
        return matcher.find();
    }

    public boolean isSpringDataDefaultMethod(String methodName) {
        final var matcher = DEFAULT_METHODS.matcher(methodName);
        return matcher.find();
    }

    public static boolean isFlux(Class<?> clazz) {
        return clazz.equals(Flux.class);
    }

    public static boolean isMono(Class<?> clazz) {
        return clazz.equals(Mono.class);
    }

    public static boolean isListOrCollection(Class<?> clazz) {
        return clazz.equals(List.class) || clazz.equals(Collection.class);
    }

    public static boolean isString(Object object) {
        return object.getClass().isAssignableFrom(String.class);
    }

    public static boolean isString(Class<?> clazz) {
        return clazz.equals(String.class);
    }

    @SneakyThrows
    public static JsonNode readTree(String tree) {
        return MAPPER.readTree(tree);
    }

    /**
     * To avoid duplicate code and for simplicity, fluxes were used in all
     * EnforcementPoints, even if the database method expects a mono. Therefore, at
     * this point it must be checked here what the return type is and transformed
     * accordingly. In addition, the case that a non-reactive type, such as a list
     * or collection, is expected is also covered.
     *
     * @param databaseObjects are the already manipulated objects, which are queried
     * with the manipulated query.
     * @param returnClassOfMethod is the type which the database method expects as
     * return type.
     * @return the manipulated objects transformed to the correct type accordingly.
     */
    @SneakyThrows // ClassNotFoundException, InterruptedException, ExecutionException
    public static <T> Object convertReturnTypeIfNecessary(Flux<T> databaseObjects, Class<?> returnClassOfMethod) {
        if (isFlux(returnClassOfMethod)) {
            return databaseObjects;
        }

        if (isMono(returnClassOfMethod)) {
            return databaseObjects.next();
        }

        if (isListOrCollection(returnClassOfMethod)) {
            return databaseObjects.collectList().toFuture().get();
        }

        throw new ClassNotFoundException("Return type of method not supported: " + returnClassOfMethod);
    }

}
