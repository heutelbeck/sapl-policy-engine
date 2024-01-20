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
package io.sapl.springdatacommon.sapl.utils;

import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@UtilityClass
public class Utilities {
    public static final String FILTER_JSON_CONTENT           = "filterJsonContent";
    public static final String FILTER_JSON_CONTENT_PREDICATE = "jsonContentFilterPredicate";
    public static final String R2DBC_QUERY_MANIPULATION      = "r2dbcQueryManipulation";
    public static final String MONGO_QUERY_MANIPULATION      = "mongoQueryManipulation";
    public static final String CONDITION                     = "condition";
    public static final String TYPE                          = "type";

    private static final Pattern PREFIX_TEMPLATE = Pattern.compile( //
            "^(find|read|get|query|search|stream)(\\p{Lu}.*?)??By");

    public boolean isMethodNameValid(String methodName) {
        Matcher matcher = PREFIX_TEMPLATE.matcher(methodName);

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

    public static boolean isInteger(Object object) {
        return object.getClass().isAssignableFrom(Integer.class);
    }

    public static boolean isString(Object object) {
        return object.getClass().isAssignableFrom(String.class);
    }

    public static boolean isInteger(Class<?> clazz) {
        return clazz.isAssignableFrom(Integer.class);
    }

    public static boolean isString(Class<?> clazz) {
        return clazz.isAssignableFrom(String.class);
    }

}
