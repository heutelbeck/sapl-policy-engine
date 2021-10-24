package io.sapl.test.mocking.attribute;

import static io.sapl.hamcrest.Matchers.val;
import static io.sapl.test.Imports.parentValue;

import io.sapl.api.interpreter.Val;
import io.sapl.test.SaplTestException;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.test.StepVerifier;

class AttributeMockForParentValueTest {

	private AttributeMockForParentValue mock;
	
	@BeforeEach
	void setUp() {
		mock = new AttributeMockForParentValue("attr.test");
	}
	
	@Test
	void test() {
		
		mock.loadMockForParentValue(parentValue(val(1)), Val.of(true));
		mock.loadMockForParentValue(parentValue(val(2)), Val.of(false));
		
		StepVerifier.create(mock.evaluate(Val.of(1), null, null))
		.expectNext(Val.of(true))
		.thenCancel().verify();
		

		StepVerifier.create(mock.evaluate(Val.of(2), null, null))
		.expectNext(Val.of(false))
		.thenCancel().verify();
	
		mock.assertVerifications();
	}

	
	@Test
	void test_noMatchingMockDefined() {
		
		mock.loadMockForParentValue(parentValue(val(1)), Val.of(true));

		Assertions.assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> {
			StepVerifier.create(mock.evaluate(Val.of(99), null, null))
			.expectNext(Val.of(false))
			.thenCancel().verify();
		});
	}

	
	@Test
	void test_errorMessage() {
		Assertions.assertThat(mock.getErrorMessageForCurrentMode()).isNotEmpty();
	}
}
