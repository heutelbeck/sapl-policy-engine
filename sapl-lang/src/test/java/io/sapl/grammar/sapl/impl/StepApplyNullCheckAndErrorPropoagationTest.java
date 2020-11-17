package io.sapl.grammar.sapl.impl;

import static org.mockito.Mockito.mock;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.reflections.Reflections;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.FilterStatement;
import io.sapl.grammar.sapl.Step;
import io.sapl.interpreter.EvaluationContext;
import lombok.extern.slf4j.Slf4j;
import reactor.test.StepVerifier;

@Slf4j
@RunWith(Parameterized.class)
public class StepApplyNullCheckAndErrorPropoagationTest {

	EvaluationContext ctx = mock(EvaluationContext.class);

	private Step step;

	public StepApplyNullCheckAndErrorPropoagationTest(Step step) {
		this.step = step;
	}

	@Parameters
	public static Collection<Object> data() throws InstantiationException, IllegalAccessException,
			IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		var reflections = new Reflections("io.sapl.grammar.sapl.impl");
		var classes = reflections.getSubTypesOf(Step.class);
		List<Object> instances = new ArrayList<Object>(classes.size());
		for (var clazz : classes) {
			if (clazz.getSimpleName().endsWith("ImplCustom")) {
				instances.add(clazz.getDeclaredConstructor().newInstance());
			}
		}
		return (List<Object>) instances;
	}

	@Before
	public void before() {
		log.trace("step: {}", step.getClass().getSimpleName());
	}
	
	@Test(expected = NullPointerException.class)
	public void nullEvaluationContext() {
		step.apply(Val.UNDEFINED, null, Val.UNDEFINED);
	}

	@Test(expected = NullPointerException.class)
	public void nullRelativeNode() {
		step.apply(Val.UNDEFINED, ctx, null);
	}

	@Test(expected = NullPointerException.class)
	public void nullParentNode() {
		step.apply(null, ctx, Val.UNDEFINED);
	}

	@Test
	public void stepsPropagateErrors() {
		var error = Val.error("TEST");
		StepVerifier.create(step.apply(error, ctx, Val.UNDEFINED)).expectNext(error).verifyComplete();
	}

	@Test(expected = NullPointerException.class)
	public void nullEvaluationContextFilter() {
		step.applyFilterStatement(Val.UNDEFINED, null, Val.UNDEFINED, 0, mock(FilterStatement.class));
	}

	@Test(expected = NullPointerException.class)
	public void nullRelativeNodeFilter() {
		step.applyFilterStatement(Val.UNDEFINED, ctx, null, 0, mock(FilterStatement.class));
	}

	@Test(expected = NullPointerException.class)
	public void nullParentNodeFilter() {
		step.applyFilterStatement(null, ctx, Val.UNDEFINED, 0, mock(FilterStatement.class));
	}

	@Test(expected = NullPointerException.class)
	public void nullFilterStatementFilter() {
		step.applyFilterStatement(Val.UNDEFINED, ctx, Val.UNDEFINED, 0, null);
	}
}
