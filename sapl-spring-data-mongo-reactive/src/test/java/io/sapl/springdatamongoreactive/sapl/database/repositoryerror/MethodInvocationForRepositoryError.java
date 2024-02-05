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
package io.sapl.springdatamongoreactive.sapl.database.repositoryerror;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.ArrayList;

import org.aopalliance.intercept.MethodInvocation;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class MethodInvocationForRepositoryError implements MethodInvocation {

    String              methodName;
    ArrayList<Class<?>> argumentClasses;
    ArrayList<Object>   argumentValues;
    Object              proceedObject;

    @Override
    public Method getMethod() {
        try {
            return RepositoryNotFoundException.class.getMethod(methodName, argumentClasses.toArray(new Class[0]));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Object[] getArguments() {
        return argumentValues.toArray(new Object[0]);
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
    public AccessibleObject getStaticPart() {
        throw new InternalError();
    }
}
