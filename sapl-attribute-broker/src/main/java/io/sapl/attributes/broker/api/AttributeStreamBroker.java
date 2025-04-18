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
package io.sapl.attributes.broker.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.Val;
import reactor.core.publisher.Flux;

public interface AttributeStreamBroker {

    Flux<Val> attributeStream(AttributeFinderInvocation invocation);

    List<String> providedFunctionsOfLibrary(String library);

    boolean isProvidedFunction(String fullyQualifiedFunctionName);

    List<String> getAllFullyQualifiedFunctions();

    Map<String, JsonNode> getAttributeSchemas();

    List<AttributeFinderSpecification> getAttributeMetatata();

    List<String> getAvailableLibraries();

    List<String> getEnvironmentAttributeCodeTemplates();

    List<String> getAttributeCodeTemplates();

    Map<String, String> getDocumentedAttributeCodeTemplates();

    Collection<LibraryDocumentation> getDocumentation();

}
