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
package io.sapl.springdatamongoreactive.sapl.utils.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import org.springframework.data.mongodb.repository.Query;

import io.sapl.springdatacommon.sapl.Enforce;
import lombok.experimental.UtilityClass;

@UtilityClass
public class AnnotationUtilities {

    /**
     * Checks whether a method has a {@link Query} annotation.
     *
     * @param method is the method to be checked.
     * @return true, if method has a {@link Query} annotation.
     */
    public static boolean hasAnnotationQuery(Method method) {
        return method.isAnnotationPresent(Query.class);
    }

    /**
     * Checks whether a method has a {@link SaplProtectedMongoReactive} annotation.
     *
     * @param method is the method to be checked.
     * @return true, if method has a {@link SaplProtectedMongoReactive} annotation.
     */
    public static boolean hasAnnotationSaplProtected(Method method) {
        return method.isAnnotationPresent(SaplProtectedMongoReactive.class);
    }

    /**
     * Checks whether a method has a {@link SaplProtectedMongoReactive} annotation.
     *
     * @param clazz is the class to be checked.
     * @return true, if method has a {@link SaplProtectedMongoReactive} annotation.
     */
    public static boolean hasAnnotationSaplProtected(Class<?> clazz) {
        return clazz.isAnnotationPresent(SaplProtectedMongoReactive.class);
    }

    /**
     * Checks whether a method has a {@link EnforceMongoReactive} annotation.
     *
     * @param method is the method to be checked.
     * @return true, if method has a {@link EnforceMongoReactive} annotation.
     */
    public static boolean hasAnnotationEnforce(Method method) {
        return method.isAnnotationPresent(EnforceMongoReactive.class);
    }

    public static Enforce convertToEnforce(EnforceMongoReactive enforceMongoReactive) {
        if (enforceMongoReactive != null) {

            return new Enforce() {
                @Override
                public Class<? extends Annotation> annotationType() {
                    return Enforce.class;
                }

                @Override
                public String subject() {
                    return checkForNullValue(enforceMongoReactive.subject());
                }

                @Override
                public String action() {
                    return checkForNullValue(enforceMongoReactive.action());
                }

                @Override
                public String resource() {
                    return checkForNullValue(enforceMongoReactive.resource());
                }

                @Override
                public String environment() {
                    return checkForNullValue(enforceMongoReactive.environment());
                }

                @Override
                public Class<?>[] staticClasses() {
                    if (enforceMongoReactive.staticClasses() != null) {
                        return enforceMongoReactive.staticClasses();
                    } else {
                        return new Class<?>[0];
                    }
                }
            };
        }
        return null;
    }

    private String checkForNullValue(String value) {
        return value == null ? "" : value;
    }
}
