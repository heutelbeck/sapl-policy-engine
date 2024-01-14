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
package io.sapl.springdatar2dbc.sapl.utils.annotation;

import io.sapl.springdatacommon.sapl.Enforce;
import lombok.experimental.UtilityClass;
import org.springframework.data.r2dbc.repository.Query;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

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
     * Checks whether a method has a {@link SaplProtectedR2dbc} annotation.
     *
     * @param method is the method to be checked.
     * @return true, if method has a {@link SaplProtectedR2dbc} annotation.
     */
    public static boolean hasAnnotationSaplProtected(Method method) {
        return method.isAnnotationPresent(SaplProtectedR2dbc.class);
    }

    /**
     * Checks whether a method has a {@link SaplProtectedR2dbc} annotation.
     *
     * @param clazz is the class to be checked.
     * @return true, if method has a {@link SaplProtectedR2dbc} annotation.
     */
    public static boolean hasAnnotationSaplProtected(Class<?> clazz) {
        return clazz.isAnnotationPresent(SaplProtectedR2dbc.class);
    }

    /**
     * Checks whether a method has a {@link Enforce} annotation.
     *
     * @param method is the method to be checked.
     * @return true, if method has a {@link Enforce} annotation.
     */
    public static boolean hasAnnotationEnforce(Method method) {
        return method.isAnnotationPresent(EnforceR2dbc.class);
    }

    public static Enforce convertToEnforce(EnforceR2dbc enforceR2dbc) {
        if (enforceR2dbc != null) {

            return new Enforce() {
                @Override
                public Class<? extends Annotation> annotationType() {
                    return Enforce.class;
                }

                @Override
                public String subject() {
                    if (enforceR2dbc.subject() != null) {
                        return enforceR2dbc.subject();
                    } else {
                        return "";
                    }
                }

                @Override
                public String action() {
                    if (enforceR2dbc.action() != null) {
                        return enforceR2dbc.action();
                    } else {
                        return "";
                    }
                }

                @Override
                public String resource() {
                    if (enforceR2dbc.resource() != null) {
                        return enforceR2dbc.resource();
                    } else {
                        return "";
                    }
                }

                @Override
                public String environment() {
                    if (enforceR2dbc.environment() != null) {
                        return enforceR2dbc.environment();
                    } else {
                        return "";
                    }
                }

                @Override
                public Class<?>[] staticClasses() {
                    if (enforceR2dbc.staticClasses() != null) {
                        return enforceR2dbc.staticClasses();
                    } else {
                        return new Class<?>[0];
                    }
                }
            };
        }
        return null;
    }
}
