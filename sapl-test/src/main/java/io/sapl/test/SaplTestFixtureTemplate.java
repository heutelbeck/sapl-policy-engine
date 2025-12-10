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
package io.sapl.test;

import io.sapl.api.model.Value;
import io.sapl.attributes.CachingAttributeBroker;
import io.sapl.attributes.InMemoryAttributeRepository;
import io.sapl.functions.DefaultFunctionBroker;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/**
 * Base template for SAPL test fixtures providing common functionality for
 * registering PIPs, function libraries, and variables.
 */
public abstract class SaplTestFixtureTemplate implements SaplTestFixture {

    private static final String ERROR_DUPLICATE_VARIABLE = "The variable context already contains a key '%s'.";

    protected final Map<String, Value> variables = HashMap.newHashMap(1);

    protected final DefaultFunctionBroker  functionBroker  = new DefaultFunctionBroker();
    protected final CachingAttributeBroker attributeBroker = new CachingAttributeBroker(
            new InMemoryAttributeRepository(Clock.systemUTC()));

    @Override
    public SaplTestFixture registerPIP(Object pip) {
        attributeBroker.loadPolicyInformationPointLibrary(pip);
        return this;
    }

    @Override
    public SaplTestFixture registerPIP(Class<?> pipClass) {
        try {
            var pipInstance = pipClass.getDeclaredConstructor().newInstance();
            attributeBroker.loadPolicyInformationPointLibrary(pipInstance);
        } catch (ReflectiveOperationException exception) {
            throw new SaplTestException("Failed to instantiate PIP class: " + pipClass.getName(), exception);
        }
        return this;
    }

    @Override
    public SaplTestFixture registerFunctionLibrary(Object library) {
        functionBroker.loadInstantiatedFunctionLibrary(library);
        return this;
    }

    @Override
    public SaplTestFixture registerFunctionLibrary(Class<?> staticLibrary) {
        functionBroker.loadStaticFunctionLibrary(staticLibrary);
        return this;
    }

    @Override
    public SaplTestFixture registerVariable(String key, Value value) {
        if (variables.containsKey(key)) {
            throw new SaplTestException(ERROR_DUPLICATE_VARIABLE.formatted(key));
        }
        variables.put(key, value);
        return this;
    }

}
