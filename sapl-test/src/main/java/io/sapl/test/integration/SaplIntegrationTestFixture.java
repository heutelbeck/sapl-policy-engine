/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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

	private static final String ERROR_MESSAGE_POLICY_PATH_NULL = "Null is not allowed for the Path pointing to the policies folder.";

	private final String pathToPoliciesFolder;

	private PolicyDocumentCombiningAlgorithm pdpAlgorithm = null;

	private Map<String, JsonNode> pdpVariables = null;

	/**
	 * Fixture for constructing a integration test case
	 * @param policyPath path relative to your class path (relative from
	 * src/main/resources, ...) to the folder containing the SAPL documents. If your
	 * policies are located at src/main/resources/yourspecialdir you only have to specify
	 * "yourspecialdir".
	 */
	public SaplIntegrationTestFixture(String policyPath) {
		this.pathToPoliciesFolder = policyPath;
	}

	/**
	 * set {@link PolicyDocumentCombiningAlgorithm} for this policy integration test
	 * @param alg the {@link PolicyDocumentCombiningAlgorithm} to be used
	 * @return the test fixture
	 */
	public SaplIntegrationTestFixture withPDPPolicyCombiningAlgorithm(PolicyDocumentCombiningAlgorithm alg) {
		this.pdpAlgorithm = alg;
		return this;
	}

	/**
	 * set the Variables-{@link Map} normally loaded from the pdp.json file
	 * @param variables a {@link Map} of variables
	 * @return the test fixture
	 */
	public SaplIntegrationTestFixture withPDPVariables(Map<String, JsonNode> variables) {
		this.pdpVariables = variables;
		return this;
	}

	@Override
	public GivenStep constructTestCaseWithMocks() {
		if (this.pathToPoliciesFolder == null || this.pathToPoliciesFolder.isEmpty()) {
			throw new SaplTestException(ERROR_MESSAGE_POLICY_PATH_NULL);
		}
		return StepBuilder.newBuilderAtGivenStep(constructPRP(), constructPDPConfig(), this.attributeCtx,
				this.functionCtx, this.variables);
	}

	@Override
	public WhenStep constructTestCase() {
		if (this.pathToPoliciesFolder == null || this.pathToPoliciesFolder.isEmpty()) {
			throw new SaplTestException(ERROR_MESSAGE_POLICY_PATH_NULL);
		}
		return StepBuilder.newBuilderAtWhenStep(constructPRP(), constructPDPConfig(), this.attributeCtx,
				this.functionCtx, this.variables);
	}

	private PolicyRetrievalPoint constructPRP() {

		SAPLInterpreter interpreter = new TestSaplInterpreter(
				CoverageAPIFactory.constructCoverageHitRecorder(resolveCoverageBaseDir()));
		return new ClasspathPolicyRetrievalPoint(Paths.get(this.pathToPoliciesFolder), interpreter);
	}

	private VariablesAndCombinatorSource constructPDPConfig() {

		return new ClasspathVariablesAndCombinatorSource(this.pathToPoliciesFolder, new ObjectMapper(),
				this.pdpAlgorithm, this.pdpVariables);
	}

}
