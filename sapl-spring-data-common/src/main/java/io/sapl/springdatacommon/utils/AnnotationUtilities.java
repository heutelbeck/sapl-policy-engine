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
package io.sapl.springdatacommon.utils;

import io.sapl.spring.method.metadata.QueryEnforce;
import lombok.experimental.UtilityClass;
import org.springframework.data.r2dbc.repository.Query;

import java.lang.reflect.Method;

@UtilityClass
public class AnnotationUtilities {

    /**
     * Checks whether a method has a {@link Query} annotation.
     *
     * @param method is the method to be checked.
     * @return true, if method has a {@link Query} annotation.
     */
    public static boolean hasAnnotationQueryR2dbc(Method method) {
        return method.isAnnotationPresent(Query.class);
    }

    public static boolean hasAnnotationQueryReactiveMongo(Method method) {
        return method.isAnnotationPresent(org.springframework.data.mongodb.repository.Query.class);
    }

    /**
     * Checks whether a method has a {@link QueryEnforce} annotation.
     *
     * @param method is the method to be checked.
     * @return true, if method has a {@link QueryEnforce} annotation.
     */
    public static boolean hasAnnotationQueryEnforce(Method method) {
        return method.isAnnotationPresent(QueryEnforce.class);
    }

}
