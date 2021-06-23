package io.sapl.test.pdp;

import java.nio.file.Path;
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

	private final Path pathToPoliciesFolder;
	
	private PolicyDocumentCombiningAlgorithm pdpAlgorithm = null;
	
	private Map<String, JsonNode> pdpVariables = null;

	/**
	 * Fixture for constructing a integration test case Expecting your policies are
	 * located at the standard path "policies/" in your resources folder.
	 */
	public SaplIntegrationTestFixture() {
		this.pathToPoliciesFolder = Paths.get("policies");
	}

	/**
	 * Fixture for constructing a integration test case
	 * 
	 * @param policyPath path relativ to your classpath (relativ from
	 *                   src/main/resources, ...) to the folder containing the sapl
	 *                   documents. If your policies are located at
	 *                   src/main/resources/yourspecialdir you only have to specify
	 *                   "yourspecialdir".
	 */
	public SaplIntegrationTestFixture(String policyPath) {
		this.pathToPoliciesFolder = Paths.get(policyPath);
	}

	/**
	 * Fixture for constructing a integration test case
	 * 
	 * @param policyPath path to the folder containing the sapl documents. If your
	 *                   policies are located at src/main/resources/yourspecialdir
	 *                   you only have to specify "yourspecialdir".
	 */
	public SaplIntegrationTestFixture(Path policyPath) {
		this.pathToPoliciesFolder = policyPath;
	}
	
	/**
	 * set {@link PolicyDocumentCombiningAlgorithm} for this policy integration test
	 * @param alg the {@link PolicyDocumentCombiningAlgorithm} to be used
	 * @return
	 */
	public SaplIntegrationTestFixture withPDPPolicyCombiningAlgorithm(PolicyDocumentCombiningAlgorithm alg) {
		this.pdpAlgorithm = alg;
		return this;
	}
	
	/**
	 * set the Variables-{@link Map} normally loaded from the pdp.json file
	 * @param variables a {@link Map} of variables
	 * @return
	 */
	public SaplIntegrationTestFixture withPDPVariables(Map<String,JsonNode> variables) {
		this.pdpVariables = variables;
		return this;
	}


	@Override
	public GivenStep constructTestCaseWithMocks() {
		if (this.pathToPoliciesFolder == null) {
			throw new SaplTestException(ERROR_MESSAGE_POLICY_PATH_NULL);
		}
		return StepBuilder.newBuilderAtGivenStep(constructPRP(), constructPDPConfig(), this.attributeCtx,
				this.functionCtx, this.variables);
	}

	@Override
	public WhenStep constructTestCase() {
		if (this.pathToPoliciesFolder == null) {
			throw new SaplTestException(ERROR_MESSAGE_POLICY_PATH_NULL);
		}
		return StepBuilder.newBuilderAtWhenStep(constructPRP(), constructPDPConfig(), this.attributeCtx,
				this.functionCtx, this.variables);
	}

	private PolicyRetrievalPoint constructPRP() {

		SAPLInterpreter interpreter = new TestSaplInterpreter(
				CoverageAPIFactory.constructCoverageHitRecorder(resolveCoverageBaseDir()));
		return new ClasspathPolicyRetrievalPoint(this.pathToPoliciesFolder, interpreter);
	}

	private VariablesAndCombinatorSource constructPDPConfig() {

		return new ClasspathVariablesAndCombinatorSource(this.pathToPoliciesFolder.toString(), new ObjectMapper(), this.pdpAlgorithm, this.pdpVariables);
	}
}
