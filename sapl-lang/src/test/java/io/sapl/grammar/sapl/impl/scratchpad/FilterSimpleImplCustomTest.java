package io.sapl.grammar.sapl.impl.scratchpad;

import org.eclipse.emf.ecore.EObject;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.grammar.sapl.BasicValue;
import io.sapl.grammar.sapl.Policy;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.grammar.sapl.impl.EObjectUtil;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.variables.VariableContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FilterSimpleImplCustomTest {

	private final static SAPLInterpreter INTERPETER = new DefaultSAPLInterpreter();

	private VariableContext variableCtx;
	private AnnotationFunctionContext functionCtx;
	private EvaluationContext ctx;

	@Before
	public void before() {
		variableCtx = new VariableContext();
		functionCtx = new AnnotationFunctionContext();
		functionCtx.loadLibrary(new FilterFunctionLibrary());
		ctx = new EvaluationContext(functionCtx, variableCtx);
	}

	@Ignore
	@Test
	public void simpleFilterTest() {
		SAPL policy = INTERPETER.parse(
				"import filter.blacken\n policy \"polly\" permit [ \"some data\", \"123456\" ] |- each filter.blacken(1,1,\"#\")");
		Policy policyAst = ((Policy) policy.getPolicyElement());
		log.info("policy element: {}", policyAst);
		BasicValue expression = (BasicValue) policyAst.getTargetExpression();
		log.info("expression = {}", expression);
		EObjectUtil.dump(expression);
		try {
			log.info("Evaluation result: {}", policy.evaluate(ctx).blockLast());
		} catch (PolicyEvaluationException e) {
			log.error("Evaluation failed: {}", e.getMessage());
		}
		// filter.apply(Val.UNDEFINED, ctx, Val.UNDEFINED);
		// expression.evaluate(ctx, Val.UNDEFINED).log().blockLast();
	}

	@Ignore
	@Test
	public void extendedFilterTest() {
		SAPL policy = INTERPETER.parse(
				"import filter.blacken\n policy \"polly\" permit { \"a\" : \"some data\", \"b\" : \"some more data\" } |- { @ : blacken(1,1,\"#\") }");
		Policy policyAst = ((Policy) policy.getPolicyElement());
		log.info("policy element: {}", policyAst);
		BasicValue expression = (BasicValue) policyAst.getTargetExpression();
		log.info("expression = {}", expression);
		EObjectUtil.dump(expression);
		try {
			AuthorizationDecision x = policy.evaluate(ctx).blockLast();
			log.info("Evaluation result: {}", x);
		} catch (PolicyEvaluationException e) {
			log.error("Evaluation failed: {}", e.getMessage());
		}
		// filter.apply(Val.UNDEFINED, ctx, Val.UNDEFINED);
		// expression.evaluate(ctx, Val.UNDEFINED).log().blockLast();
	}

	@Ignore
	@Test
	public void BasicFunctionTest() {
		SAPL policy = INTERPETER
				.parse("import filter.blacken\n policy \"polly\" permit blacken(\"some data\",1,1,\"#\")");
		Policy policyAst = ((Policy) policy.getPolicyElement());
		log.info("policy element: {}", policyAst);
		EObject expression = policyAst.getTargetExpression();
		log.info("expression = {}", expression);
		EObjectUtil.dump(expression);
		try {
			AuthorizationDecision x = policy.evaluate(ctx).blockLast();
			log.info("Evaluation result: {}", x);
		} catch (PolicyEvaluationException e) {
			log.error("Evaluation failed: {}", e.getMessage());
		}
	}

	@Ignore
	@Test
	public void BasicIdentifierTest() {
		SAPL policy = INTERPETER
				.parse("import filter.blacken\n policy \"polly\" permit blacken(\"some data\",1,1,\"#\")");
		Policy policyAST = ((Policy) policy.getPolicyElement());
		log.info("policy element: {}", policyAST);
		EObject expression = policyAST.getTargetExpression();
		log.info("expression = {}", expression);
		EObjectUtil.dump(expression);
		try {
			AuthorizationDecision x = policy.evaluate(ctx).blockLast();
			log.info("Evaluation result: {}", x);
		} catch (PolicyEvaluationException e) {
			log.error("Evaluation failed: {}", e.getMessage());
		}
	}
}
