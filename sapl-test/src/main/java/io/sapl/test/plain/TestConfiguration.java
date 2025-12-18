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
package io.sapl.test.plain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.CombiningAlgorithm;

/**
 * Complete configuration for test execution.
 * <p>
 * Use the builder to construct a configuration:
 *
 * <pre>{@code
 * var config = TestConfiguration.builder().withSaplDocument(doc1).withSaplDocument(doc2).withSaplTestDocument(testDoc)
 *         .withDefaultAlgorithm(CombiningAlgorithm.DENY_OVERRIDES).withFunctionLibrary(TemporalFunctionLibrary.class)
 *         .build();
 * }</pre>
 */
public record TestConfiguration(
        List<SaplDocument> saplDocuments,
        List<SaplTestDocument> saplTestDocuments,
        CombiningAlgorithm defaultAlgorithm,
        Map<String, Value> pdpVariables,
        List<Class<?>> functionLibraries,
        List<Object> policyInformationPoints,
        boolean failFast) {

    /**
     * Creates a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for TestConfiguration.
     */
    public static class Builder {
        private final List<SaplDocument>     saplDocuments           = new ArrayList<>();
        private final List<SaplTestDocument> saplTestDocuments       = new ArrayList<>();
        private CombiningAlgorithm           defaultAlgorithm        = CombiningAlgorithm.DENY_OVERRIDES;
        private final Map<String, Value>     pdpVariables            = new HashMap<>();
        private final List<Class<?>>         functionLibraries       = new ArrayList<>();
        private final List<Object>           policyInformationPoints = new ArrayList<>();
        private boolean                      failFast                = false;

        /**
         * Adds a SAPL document to test.
         */
        public Builder withSaplDocument(SaplDocument document) {
            this.saplDocuments.add(document);
            return this;
        }

        /**
         * Adds multiple SAPL documents to test.
         */
        public Builder withSaplDocuments(List<SaplDocument> documents) {
            this.saplDocuments.addAll(documents);
            return this;
        }

        /**
         * Adds a test document.
         */
        public Builder withSaplTestDocument(SaplTestDocument testDocument) {
            this.saplTestDocuments.add(testDocument);
            return this;
        }

        /**
         * Adds multiple test documents.
         */
        public Builder withSaplTestDocuments(List<SaplTestDocument> testDocuments) {
            this.saplTestDocuments.addAll(testDocuments);
            return this;
        }

        /**
         * Sets the default combining algorithm for integration tests.
         */
        public Builder withDefaultAlgorithm(CombiningAlgorithm algorithm) {
            this.defaultAlgorithm = algorithm;
            return this;
        }

        /**
         * Adds a PDP variable.
         */
        public Builder withVariable(String name, Value value) {
            this.pdpVariables.put(name, value);
            return this;
        }

        /**
         * Adds multiple PDP variables.
         */
        public Builder withVariables(Map<String, Value> variables) {
            this.pdpVariables.putAll(variables);
            return this;
        }

        /**
         * Adds a function library class.
         */
        public Builder withFunctionLibrary(Class<?> libraryClass) {
            this.functionLibraries.add(libraryClass);
            return this;
        }

        /**
         * Adds multiple function library classes.
         */
        public Builder withFunctionLibraries(List<Class<?>> libraryClasses) {
            this.functionLibraries.addAll(libraryClasses);
            return this;
        }

        /**
         * Adds a policy information point instance.
         */
        public Builder withPolicyInformationPoint(Object pip) {
            this.policyInformationPoints.add(pip);
            return this;
        }

        /**
         * Adds multiple policy information point instances.
         */
        public Builder withPolicyInformationPoints(List<Object> pips) {
            this.policyInformationPoints.addAll(pips);
            return this;
        }

        /**
         * Enables fail-fast mode (stop on first failure).
         */
        public Builder withFailFast(boolean failFast) {
            this.failFast = failFast;
            return this;
        }

        /**
         * Builds the configuration.
         */
        public TestConfiguration build() {
            return new TestConfiguration(List.copyOf(saplDocuments), List.copyOf(saplTestDocuments), defaultAlgorithm,
                    Map.copyOf(pdpVariables), List.copyOf(functionLibraries), List.copyOf(policyInformationPoints),
                    failFast);
        }
    }
}
