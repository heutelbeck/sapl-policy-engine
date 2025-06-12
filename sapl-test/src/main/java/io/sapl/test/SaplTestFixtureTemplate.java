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

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.interpreter.Val;
import io.sapl.attributes.broker.impl.AnnotationPolicyInformationPointLoader;
import io.sapl.attributes.broker.impl.CachingAttributeStreamBroker;
import io.sapl.attributes.broker.impl.InMemoryPolicyInformationPointDocumentationProvider;
import io.sapl.attributes.documentation.api.PolicyInformationPointDocumentationProvider;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.validation.ValidatorFactory;

public abstract class SaplTestFixtureTemplate implements SaplTestFixture {

    protected final Map<String, Val> variables = new HashMap<>(1);

    protected final AnnotationFunctionContext                   functionCtx           = new AnnotationFunctionContext();
    protected final CachingAttributeStreamBroker                attributeStreamBroker = new CachingAttributeStreamBroker();
    protected final PolicyInformationPointDocumentationProvider docsProvider          = new InMemoryPolicyInformationPointDocumentationProvider();
    protected final AnnotationPolicyInformationPointLoader      loader                = new AnnotationPolicyInformationPointLoader(
            attributeStreamBroker, docsProvider, new ValidatorFactory(new ObjectMapper()));

    @Override
    public SaplTestFixture registerPIP(Object pip) throws InitializationException {
        this.loader.loadPolicyInformationPoint(pip);
        return this;
    }

    @Override
    public SaplTestFixture registerPIP(Class<?> pipClass) throws InitializationException {
        this.loader.loadPolicyInformationPoint(pipClass);
        return this;
    }

    @Override
    public SaplTestFixture registerFunctionLibrary(Object library) throws InitializationException {
        this.functionCtx.loadLibrary(library);
        return this;
    }

    @Override
    public SaplTestFixture registerFunctionLibrary(Class<?> staticLibrary) throws InitializationException {
        this.functionCtx.loadLibrary(staticLibrary);
        return this;
    }

    @Override
    public SaplTestFixture registerVariable(String key, Val value) {
        if (this.variables.containsKey(key)) {
            throw new SaplTestException("The VariableContext already contains a key \"" + key + "\"");
        }
        this.variables.put(key, value);
        return this;
    }

}
