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
package io.sapl.springdatamongoreactive.sapl.database;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.ArrayList;

import org.aopalliance.intercept.MethodInvocation;

import lombok.AllArgsConstructor;
import lombok.NonNull;

@AllArgsConstructor
public class MethodInvocationForTesting implements MethodInvocation {

    String              methodName;
    ArrayList<Class<?>> argumentClasses;
    ArrayList<Object>   argumentValues;
    Object              proceedObject;

    @Override
    public @NonNull Method getMethod() {
        try {
            return UserReactiveMongoRepository.class.getMethod(methodName,
                    argumentClasses.toArray(new Class[argumentClasses.size()]));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public @NonNull Object[] getArguments() {
        return argumentValues.toArray(new Object[argumentValues.size()]);
    }

    @Override
    public Object proceed() {
        return proceedObject;
    }

    @Override
    public Object getThis() {
        return this;
    }

    @Override
    public @NonNull AccessibleObject getStaticPart() {
        return getMethod();
    }
}
