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
package io.sapl.test.integration;

import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.pdp.config.VariablesAndCombinatorSource;
import io.sapl.prp.PolicyRetrievalPoint;
import io.sapl.test.SaplTestException;
import io.sapl.test.SaplTestFixtureTemplate;
import io.sapl.test.coverage.api.CoverageAPIFactory;
import io.sapl.test.lang.TestSaplInterpreter;
import io.sapl.test.steps.GivenStep;
import io.sapl.test.steps.WhenStep;

public class SaplIntegrationTestFixture extends SaplTestFixtureTemplate {

    private static final String ERROR_MESSAGE_POLICY_FOLDER_PATH_NULL              = "Null is not allowed for the Path pointing to the policies folder.";
    private static final String ERROR_MESSAGE_POLICY_PATHS_NULL_OR_SINGLE_VALUE    = "List of policies paths needs to contain at least 2 values.";
    private static final String ERROR_MESSAGE_INPUT_DOCUMENTS_NULL_OR_SINGLE_VALUE = "List input documents needs to contain at least 2 values.";
    private final ObjectMapper  objectMapper                                       = new ObjectMapper();

    private PolicyDocumentCombiningAlgorithm pdpAlgorithm = null;
    private Map<String, Val>                 pdpVariables = null;

    private final Supplier<PolicyRetrievalPoint>         prpSupplier;
    private final Supplier<VariablesAndCombinatorSource> variablesAndCombinatorSourceSupplier;

    /**
     * Fixture for constructing an integration test case
     *
     * @param folderPath path relative to your class path (relative from
     * src/main/resources, ...) to the folder containing the SAPL documents. If your
     * policies are located at src/main/resources/yourSpecialDirectory you only have
     * to specify "yourSpecialDirectory".
     */
    public SaplIntegrationTestFixture(final String folderPath) {
        prpSupplier                          = () -> this.constructPRP(true, folderPath, null);
        variablesAndCombinatorSourceSupplier = () -> this.constructPDPConfig(folderPath);
    }

    public SaplIntegrationTestFixture(final String pdpConfigPath, final Collection<String> policyPaths) {
        prpSupplier                          = () -> this.constructPRP(false, null, policyPaths);
        variablesAndCombinatorSourceSupplier = () -> this.constructPDPConfig(pdpConfigPath);
    }

    public SaplIntegrationTestFixture(final Collection<String> documentStrings, final String pdpConfig) {
        prpSupplier                          = () -> this.constructInputStringPRP(documentStrings);
        variablesAndCombinatorSourceSupplier = () -> this.constructInputStringPDPConfig(pdpConfig);
    }

    /**
     * set {@link PolicyDocumentCombiningAlgorithm} for this policy integration test
     *
     * @param alg the {@link PolicyDocumentCombiningAlgorithm} to be used
     * @return the test fixture
     */
    public SaplIntegrationTestFixture withPDPPolicyCombiningAlgorithm(PolicyDocumentCombiningAlgorithm alg) {
        this.pdpAlgorithm = alg;
        return this;
    }

    /**
     * set the Variables-{@link Map} normally loaded from the pdp.json file
     *
     * @param variables a {@link Map} of variables
     * @return the test fixture
     */
    public SaplIntegrationTestFixture withPDPVariables(Map<String, Val> variables) {
        this.pdpVariables = variables;
        return this;
    }

    @Override
    public GivenStep constructTestCaseWithMocks() {
        return StepBuilder.newBuilderAtGivenStep(prpSupplier.get(), variablesAndCombinatorSourceSupplier.get(),
                this.attributeCtx, this.functionCtx, this.variables);
    }

    @Override
    public WhenStep constructTestCase() {
        return StepBuilder.newBuilderAtWhenStep(prpSupplier.get(), variablesAndCombinatorSourceSupplier.get(),
                this.attributeCtx, this.functionCtx, this.variables);
    }

    private PolicyRetrievalPoint constructPRP(final boolean usePolicyFolder, final String pathToPoliciesFolder,
            final Collection<String> policyPaths) {
        final var interpreter = getSaplInterpreter();

        if (usePolicyFolder) {
            if (pathToPoliciesFolder == null || pathToPoliciesFolder.isEmpty()) {
                throw new SaplTestException(ERROR_MESSAGE_POLICY_FOLDER_PATH_NULL);
            }
            return new ClasspathPolicyRetrievalPoint(Paths.get(pathToPoliciesFolder), interpreter);
        } else {
            if (policyPaths == null || policyPaths.size() < 2) {
                throw new SaplTestException(ERROR_MESSAGE_POLICY_PATHS_NULL_OR_SINGLE_VALUE);
            }
            return new ClasspathPolicyRetrievalPoint(policyPaths, interpreter);
        }
    }

    private PolicyRetrievalPoint constructInputStringPRP(final Collection<String> documentStrings) {
        final var interpreter = getSaplInterpreter();

        if (documentStrings == null || documentStrings.size() < 2) {
            throw new SaplTestException(ERROR_MESSAGE_INPUT_DOCUMENTS_NULL_OR_SINGLE_VALUE);
        }

        return new InputStringPolicyRetrievalPoint(documentStrings, interpreter);
    }

    private SAPLInterpreter getSaplInterpreter() {
        return new TestSaplInterpreter(CoverageAPIFactory.constructCoverageHitRecorder(resolveCoverageBaseDir()));
    }

    private VariablesAndCombinatorSource constructPDPConfig(final String pdpConfigPath) {
        final var actualConfigPath = Objects.requireNonNullElse(pdpConfigPath, "");

        return new ClasspathVariablesAndCombinatorSource(actualConfigPath, objectMapper, this.pdpAlgorithm,
                this.pdpVariables);
    }

    private VariablesAndCombinatorSource constructInputStringPDPConfig(final String input) {
        final var pdpConfig = (input == null || input.isEmpty()) ? "{}" : input;

        return new InputStringVariablesAndCombinatorSource(pdpConfig, objectMapper, this.pdpAlgorithm,
                this.pdpVariables);
    }

}
