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
package io.sapl.attributes.broker.impl;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.attributes.broker.api.AttributeBrokerException;
import io.sapl.attributes.documentation.api.FunctionType;
import io.sapl.attributes.documentation.api.LibraryDocumentation;
import io.sapl.attributes.documentation.api.LibraryFunctionDocumentation;
import io.sapl.attributes.documentation.api.PolicyInformationPointDocumentationProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InMemoryPolicyInformationPointDocumentationProvider
        implements PolicyInformationPointDocumentationProvider {

    private final Map<String, LibraryDocumentation> pipRegistry = new HashMap<>();

    private final Object lock = new Object();

    @Override
    public void loadPolicyInformationPoint(LibraryDocumentation pipDocumentation) {
        synchronized (lock) {
            final var pipName = pipDocumentation.namespace();
            if (pipRegistry.containsKey(pipName)) {
                throw new AttributeBrokerException(
                        String.format("Cannot load documentation for %s. Name already in use.", pipDocumentation));
            }
            pipRegistry.put(pipName, pipDocumentation);
        }
    }

    @Override
    public void unloadPolicyInformationPoint(String name) {
        synchronized (lock) {
            pipRegistry.remove(name);
        }
    }

    @Override
    public List<String> providedFunctionsOfLibrary(String libraryName) {
        synchronized (lock) {
            final var library = pipRegistry.get(libraryName);
            if (null == library) {
                return List.of();
            }
            return library.attributes().stream().map(LibraryFunctionDocumentation::fullyQualifiedName)
                    .map(n -> n.substring(libraryName.length() + 1)).toList();
        }
    }

    @Override
    public boolean isProvidedFunction(String fullyQualifiedFunctionName) {
        synchronized (lock) {
            for (var pip : pipRegistry.values()) {
                for (var finder : pip.attributes()) {
                    if (finder.fullyQualifiedName().equals(fullyQualifiedFunctionName)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    @Override
    public List<String> getAllFullyQualifiedFunctions() {
        synchronized (lock) {
            return pipRegistry.values().stream().flatMap(spec -> spec.attributes().stream())
                    .map(LibraryFunctionDocumentation::fullyQualifiedName).toList();
        }
    }

    @Override
    public Map<String, JsonNode> getAttributeSchemas() {
        synchronized (lock) {
            final var result = new HashMap<String, JsonNode>();
            for (var pip : pipRegistry.values()) {
                for (var finder : pip.attributes()) {
                    result.put(finder.fullyQualifiedName(), finder.returnTypeSchema());
                }
            }
            return result;
        }
    }

    @Override
    public List<LibraryFunctionDocumentation> getAttributeMetatata() {
        synchronized (lock) {
            final var result = new ArrayList<LibraryFunctionDocumentation>();
            for (var pip : pipRegistry.values()) {
                result.addAll(pip.attributes());
            }
            return result;
        }
    }

    @Override
    public List<String> getAvailableLibraries() {
        synchronized (lock) {
            return pipRegistry.keySet().stream().toList();
        }
    }

    @Override
    public List<String> getEnvironmentAttributeCodeTemplates() {
        synchronized (lock) {
            final var result = new ArrayList<String>();
            for (var pip : pipRegistry.values()) {
                pip.attributes().stream().filter(d -> d.type() == FunctionType.ENVIRONMENT_ATTRIBUTE)
                        .map(LibraryFunctionDocumentation::codeTemplate).forEach(result::add);
            }
            return result;
        }
    }

    @Override
    public List<String> getAttributeCodeTemplates() {
        synchronized (lock) {
            final var result = new ArrayList<String>();
            for (var pip : pipRegistry.values()) {
                pip.attributes().stream().filter(d -> d.type() == FunctionType.ATTRIBUTE)
                        .map(LibraryFunctionDocumentation::codeTemplate).forEach(result::add);
            }
            return result;
        }
    }

    @Override
    public Map<String, String> getDocumentedAttributeCodeTemplates() {
        synchronized (lock) {
            final var result = new HashMap<String, String>();
            for (var pip : pipRegistry.values()) {
                for (var finder : pip.attributes()) {
                    result.put(finder.codeTemplate(), finder.documentationMarkdown());
                }
            }
            return result;
        }
    }

    @Override
    public List<LibraryDocumentation> getDocumentation() {
        synchronized (lock) {
            return pipRegistry.values().stream().toList();
        }
    }

}
