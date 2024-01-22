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

import static org.junit.jupiter.api.Assertions.*;

import java.lang.annotation.Annotation;

import org.junit.jupiter.api.Test;

import io.sapl.springdatacommon.sapl.Enforce;

class AnnotationUtilitiesTests {

    @Test
    void when_enforceMongoReactiveHasValues_then_setValuesOfEnforceAnnotationAsWell() {
        // GIVEN
        String subject     = "Subject";
        String action      = "Action";
        String resource    = "Resource";
        String environment = "Environment";

        EnforceR2dbc enforceMongoReactive = new EnforceR2dbc() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return EnforceR2dbc.class;
            }

            @Override
            public String subject() {
                return subject;
            }

            @Override
            public String action() {
                return action;
            }

            @Override
            public String resource() {
                return resource;
            }

            @Override
            public String environment() {
                return environment;
            }

            @Override
            public Class<?>[] staticClasses() {
                Class<?>[] classes = new Class<?>[1];
                classes[0] = Object.class;
                return classes;
            }
        };

        Enforce expectedEnforce = new Enforce() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Enforce.class;
            }

            @Override
            public String subject() {
                return subject;
            }

            @Override
            public String action() {
                return action;
            }

            @Override
            public String resource() {
                return resource;
            }

            @Override
            public String environment() {
                return environment;
            }

            @Override
            public Class<?>[] staticClasses() {
                Class<?>[] classes = new Class<?>[1];
                classes[0] = Object.class;
                return classes;
            }
        };

        Enforce enforceResult = AnnotationUtilities.convertToEnforce(enforceMongoReactive);

        assertEquals(expectedEnforce.annotationType(), enforceResult.annotationType());
        assertEquals(expectedEnforce.subject(), enforceResult.subject());
        assertEquals(expectedEnforce.action(), enforceResult.action());
        assertEquals(expectedEnforce.resource(), enforceResult.resource());
        assertEquals(expectedEnforce.environment(), enforceResult.environment());
        assertEquals(expectedEnforce.staticClasses()[0], enforceResult.staticClasses()[0]);
    }

    @Test
    void when_enforceMongoReactiveHasNullValues_then_setValuesOfEnforceAnnotationToEmptyString() {
        // GIVEN

        EnforceR2dbc enforceMongoReactive = new EnforceR2dbc() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return EnforceR2dbc.class;
            }

            @Override
            public String subject() {
                return null;
            }

            @Override
            public String action() {
                return null;
            }

            @Override
            public String resource() {
                return null;
            }

            @Override
            public String environment() {
                return null;
            }

            @Override
            public Class<?>[] staticClasses() {
                return null;
            }
        };

        Enforce expectedEnforce = new Enforce() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Enforce.class;
            }

            @Override
            public String subject() {
                return "";
            }

            @Override
            public String action() {
                return "";
            }

            @Override
            public String resource() {
                return "";
            }

            @Override
            public String environment() {
                return "";
            }

            @Override
            public Class<?>[] staticClasses() {
                return new Class<?>[0];
            }
        };

        Enforce enforceResult = AnnotationUtilities.convertToEnforce(enforceMongoReactive);

        assertEquals(expectedEnforce.annotationType(), enforceResult.annotationType());
        assertEquals(expectedEnforce.subject(), enforceResult.subject());
        assertEquals(expectedEnforce.action(), enforceResult.action());
        assertEquals(expectedEnforce.resource(), enforceResult.resource());
        assertEquals(expectedEnforce.environment(), enforceResult.environment());
        assertEquals(expectedEnforce.staticClasses().length, enforceResult.staticClasses().length);
    }

}
