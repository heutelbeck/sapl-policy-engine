package io.sapl.test.lang;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.sapl.grammar.sapl.SAPL;
import io.sapl.test.coverage.api.CoverageHitRecorder;

public class TestSaplInterpreterTest {

	private final String POLICY_ID = "test";
	
	@Test
	void testTestModeImplAreCreatedIfSystemPropertyIsNotSet() {
		var interpreter = new TestSaplInterpreter(Mockito.mock(CoverageHitRecorder.class));
		
		final String policyDocument = "policy \"" + POLICY_ID + "\" permit";
		SAPL document = interpreter.parse(policyDocument);
		
		Assertions.assertThat(document.getPolicyElement() instanceof PolicyImplCustomCoverage).isTrue();
	}
	
	@Test
	void testTestModeImplAreCreatedIfSystemPropertyIsTrue() {
		System.setProperty("io.sapl.test.coverage.collect", "true");
		var interpreter = new TestSaplInterpreter(Mockito.mock(CoverageHitRecorder.class));
		
		final String policyDocument = "policy \"" + POLICY_ID + "\" permit";
		SAPL document = interpreter.parse(policyDocument);
		
		Assertions.assertThat(document.getPolicyElement() instanceof PolicyImplCustomCoverage).isTrue();
		System.clearProperty("io.sapl.test.coverage.collect");
	}
	
	@Test
	void testTestModeImplAreCreatedIfSystemPropertyIsFalse() {
		System.setProperty("io.sapl.test.coverage.collect", "false");
		var interpreter = new TestSaplInterpreter(Mockito.mock(CoverageHitRecorder.class));
		
		final String policyDocument = "policy \"" + POLICY_ID + "\" permit";
		SAPL document = interpreter.parse(policyDocument);
		
		Assertions.assertThat(document.getPolicyElement() instanceof PolicyImplCustomCoverage).isFalse();
		System.clearProperty("io.sapl.test.coverage.collect");
	}


}
