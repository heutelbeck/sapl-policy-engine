package io.sapl.grammar.sapl.impl.scratchpad;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.grammar.sapl.Policy;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.grammar.sapl.Statement;
import io.sapl.grammar.sapl.impl.util.EObjectUtil;
import io.sapl.grammar.tests.MockPolicyInformationPoint;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.interpreter.variables.VariableContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StepProcessingTest {

	private final static SAPLInterpreter INTERPETER = new DefaultSAPLInterpreter();
	private final static ObjectMapper MAPPER = new ObjectMapper();

	private VariableContext variableCtx;
	private AnnotationFunctionContext functionCtx;
	private AnnotationAttributeContext attributeCtx;
	private EvaluationContext ctx;

	@Before
	public void before() throws JsonMappingException, JsonProcessingException {
		var subject = MAPPER.readTree("{ \"details\" : { \"id\" : [ 0, 1, 2, 3, 4, 5, 6 ], \"age\" : 22 } }");
		AuthorizationSubscription sub = AuthorizationSubscription.of(subject, null, null);
		attributeCtx = new AnnotationAttributeContext();
		attributeCtx.loadPolicyInformationPoint(new MockPolicyInformationPoint());
		variableCtx = new VariableContext(sub);
		functionCtx = new AnnotationFunctionContext();
		functionCtx.loadLibrary(new FilterFunctionLibrary());
		ctx = new EvaluationContext(attributeCtx, functionCtx, variableCtx);
	}

	@Ignore
	@Test
	public void BasicIdentifierTest() {
		// SAPL policy = INTERPETER.parse("policy \"polly\" permit where
		// subject.details.id;");
		SAPL policy = INTERPETER.parse("policy \"polly\" permit where subject;");
		// variableCtx.put("subject", subject);
		Policy policyAST = ((Policy) policy.getPolicyElement());
		Statement statement = policyAST.getBody().getStatements().get(0);

		log.info("statement {}", statement);
		EObjectUtil.dump(statement);

		// EObject expression = policyAST.getTargetExpression();
//		log.info("expression = {}", expression);
		EObjectUtil.dump(policyAST);
//		log.info("---------------------------");
		try {
			AuthorizationDecision x = policy.evaluate(ctx).blockLast();
			log.info("Evaluation result: {}", x);
		} catch (PolicyEvaluationException e) {
			log.error("Evaluation failed: {}", e.getMessage());
		}
		log.info("---------------------------");
	}

	@Ignore
	@Test
	public void arraySlicingTest() {
		// SAPL policy = INTERPETER.parse("policy \"polly\" permit where
		// subject.details.id;");
		SAPL policy = INTERPETER.parse("policy \"polly\" permit where subject.details.id[-2:];");
		// variableCtx.put("subject", subject);
		Policy policyAST = ((Policy) policy.getPolicyElement());
		Statement statement = policyAST.getBody().getStatements().get(0);

		log.info("statement {}", statement);
		EObjectUtil.dump(statement);

		// EObject expression = policyAST.getTargetExpression();
//		log.info("expression = {}", expression);
		EObjectUtil.dump(policyAST);
//		log.info("---------------------------");
		try {
			AuthorizationDecision x = policy.evaluate(ctx).blockLast();
			log.info("Evaluation result: {}", x);
		} catch (PolicyEvaluationException e) {
			log.error("Evaluation failed: {}", e.getMessage());
		}
		log.info("---------------------------");
	}

	@Ignore
	@Test
	public void attributeTest() {
		SAPL policy = INTERPETER.parse("policy \"polly\" permit where subject.<pip.mixed>;subject.<pip.booleans>;");
		try {
			AuthorizationDecision x = policy.evaluate(ctx).blockLast();
			log.info("Evaluation result: {}", x);
		} catch (PolicyEvaluationException e) {
			log.error("Evaluation failed: {}", e.getMessage());
		}
		log.info("---------------------------");
	}

	@Ignore
	@Test
	public void setValuesTest() {
		SAPL policy = INTERPETER
				.parse("policy \"polly\" permit where var something = subject.<pip.booleans>; something;");
		try {
			AuthorizationDecision x = policy.evaluate(ctx).blockLast();
			log.info("Evaluation result: {}", x);
		} catch (PolicyEvaluationException e) {
			log.error("Evaluation failed: {}", e.getMessage());
		}
		log.info("---------------------------");
	}

	@Ignore
	@Test
	public void attributeUnionTest() {
		SAPL policy = INTERPETER.parse("policy \"polly\" permit where subject.details[\"id\", \"age\"];");
		try {
			AuthorizationDecision x = policy.evaluate(ctx).blockLast();
			log.info("Evaluation result: {}", x);
		} catch (PolicyEvaluationException e) {
			log.error("Evaluation failed: {}", e.getMessage());
		}
		log.info("---------------------------");
	}

	@Ignore
	@Test
	public void conditionStepTest() {
		SAPL policy = INTERPETER.parse("policy \"polly\" permit where subject.details[?(@ != undefined)];");
		try {
			AuthorizationDecision x = policy.evaluate(ctx).blockLast();
			log.info("Evaluation result: {}", x);
		} catch (PolicyEvaluationException e) {
			log.error("Evaluation failed: {}", e.getMessage());
		}
		log.info("---------------------------");
	}

	@Ignore
	@Test
	public void expressionStepTest() {
		SAPL policy = INTERPETER.parse("policy \"polly\" permit where subject.details.id[(1.8+2)];");
		try {
			AuthorizationDecision x = policy.evaluate(ctx).blockLast();
			log.info("Evaluation result: {}", x);
		} catch (PolicyEvaluationException e) {
			log.error("Evaluation failed: {}", e.getMessage());
		}
		log.info("---------------------------");
	}

	@Ignore
	@Test
	public void indexStepTest() {
		SAPL policy = INTERPETER.parse("policy \"polly\" permit where subject.details.id[5.5];");
		try {
			AuthorizationDecision x = policy.evaluate(ctx).blockLast();
			log.info("Evaluation result: {}", x);
		} catch (PolicyEvaluationException e) {
			log.error("Evaluation failed: {}", e.getMessage());
		}
		log.info("---------------------------");
	}

	@Ignore
	@Test
	public void indexUnionTest() {
		SAPL policy = INTERPETER.parse("policy \"polly\" permit where subject.details.id[0,2,0,4];");
		try {
			AuthorizationDecision x = policy.evaluate(ctx).blockLast();
			log.info("Evaluation result: {}", x);
		} catch (PolicyEvaluationException e) {
			log.error("Evaluation failed: {}", e.getMessage());
		}
		log.info("---------------------------");
	}

	@Ignore
	@Test
	public void keyStepOnArrayTest() {
		SAPL policy = INTERPETER.parse(
				"policy \"p\" permit where [ { \"name\" : \"Willi Wonka\", \"job\" : \"creep\" }, { \"name\" : \"Jack the Ripper\", \"job\" : \"recreational surgeon\" }].name;");
		EObjectUtil.dump(policy);
		try {
			AuthorizationDecision x = policy.evaluate(ctx).blockLast();
			log.info("Evaluation result: {}", x);
		} catch (PolicyEvaluationException e) {
			log.error("Evaluation failed: {}", e.getMessage());
		}
		log.info("---------------------------");
	}

	@Ignore
	@Test
	public void recusiveWildcardTest() {
		SAPL policy = INTERPETER.parse(
				"policy \"p\" permit where [ { \"name\" : \"Willi Wonka\", \"job\" : \"creep\", \"ids\" : [1,2,3,4,5] }, { \"name\" : \"Jack the Ripper\", \"job\" : \"recreational surgeon\" }]..*;");
		EObjectUtil.dump(policy);
		try {
			AuthorizationDecision x = policy.evaluate(ctx).blockLast();
			log.info("Evaluation result: {}", x);
		} catch (PolicyEvaluationException e) {
			log.error("Evaluation failed: {}", e.getMessage());
		}
		log.info("---------------------------");
	}

	@Ignore
	@Test
	public void recusiveKeyTest() {
		SAPL policy = INTERPETER.parse(
				"policy \"p\" permit where [ { \"name\" : \"Willi Wonka\", \"job\" : \"creep\", \"ids\" : [1,2,3,4, { \"job\":\"killer clown\"}] }, { \"name\" : \"Jack the Ripper\", \"job\" : \"recreational surgeon\" }]..job;");
		EObjectUtil.dump(policy);
		try {
			AuthorizationDecision x = policy.evaluate(ctx).blockLast();
			log.info("Evaluation result: {}", x);
		} catch (PolicyEvaluationException e) {
			log.error("Evaluation failed: {}", e.getMessage());
		}
		log.info("---------------------------");
	}

	@Ignore
	@Test
	public void recusiveIndexTest() {
		SAPL policy = INTERPETER.parse(
				"policy \"p\" permit where [ { \"name\" : \"Willi Wonka\", \"job\" : \"creep\", \"ids\" : [1,2,3,4, { \"job\":\"killer clown\"}] }, { \"name\" : \"Jack the Ripper\", \"job\" : \"recreational surgeon\" }]..[0];"
						+ "job;");
		EObjectUtil.dump(policy);
		try {
			AuthorizationDecision x = policy.evaluate(ctx).blockLast();
			log.info("Evaluation result: {}", x);
		} catch (PolicyEvaluationException e) {
			log.error("Evaluation failed: {}", e.getMessage());
		}
		log.info("---------------------------");
	}

	@Ignore
	@Test
	public void expressionTest() {
		SAPL policy = INTERPETER.parse("policy \"p\" permit (1+8-6);");
		EObjectUtil.dump(policy);
		try {
			AuthorizationDecision x = policy.evaluate(ctx).blockLast();
			log.info("Evaluation result: {}", x);
		} catch (PolicyEvaluationException e) {
			log.error("Evaluation failed: {}", e.getMessage());
		}
		log.info("---------------------------");
	}
}
