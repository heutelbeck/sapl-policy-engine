package io.sapl.test.mocking.attribute;

import static io.sapl.hamcrest.Matchers.val;
import static io.sapl.test.Imports.*;

import java.util.LinkedList;
import java.util.List;

import io.sapl.api.interpreter.Val;
import io.sapl.test.SaplTestException;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class AttributeMockForParentValueAndArgumentsTest {
	
	private AttributeMockForParentValueAndArguments mock;
	
	@BeforeEach
	void setUp() {
		mock = new AttributeMockForParentValueAndArguments("attr.test");
	}
	
	@Test
	void test() {
		mock.loadMockForParentValueAndArguments(whenAttributeParams(parentValue(val(true)), arguments(val(1), val(1))), Val.of(true));
		mock.loadMockForParentValueAndArguments(whenAttributeParams(parentValue(val(true)), arguments(val(1), val(2))), Val.of(false));
		
		
		List<Flux<Val>> arguments = new LinkedList<>();
		arguments.add(Flux.just(Val.of(1)));
		arguments.add(Flux.just(Val.of(1), Val.of(2)));
		
		StepVerifier.create(mock.evaluate(Val.of(true), null, arguments))
		.expectNext(Val.of(true))
		.expectNext(Val.of(false))
		.thenCancel().verify();
	
		mock.assertVerifications();
	}
	
	@Test
	void test_notMatchingMockForParentValue() {
		mock.loadMockForParentValueAndArguments(whenAttributeParams(parentValue(val(true)), arguments(val(1))), Val.of(true));
		
		
		List<Flux<Val>> arguments = new LinkedList<>();
		arguments.add(Flux.just(Val.of(1)));
		
		
		Assertions.assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> {
			StepVerifier.create(mock.evaluate(Val.of(false), null, arguments))
			.expectNext(Val.of(true))
			.thenCancel().verify();			
		});
	}
	
	@Test
	void test_noMatchingMockForArguments() {
		mock.loadMockForParentValueAndArguments(whenAttributeParams(parentValue(val(true)), arguments(val(1), val(1))), Val.of(true));
		
		
		List<Flux<Val>> arguments = new LinkedList<>();
		arguments.add(Flux.just(Val.of(99)));
		arguments.add(Flux.just(Val.of(99)));
		
		StepVerifier.create(mock.evaluate(Val.of(true), null, arguments))
			.expectError()
			.verify();
	}
	
	@Test
	void test_argumentCountNotMatching() {
		mock.loadMockForParentValueAndArguments(whenAttributeParams(parentValue(val(true)), arguments(val(1), val(1))), Val.of(true));
		
		
		List<Flux<Val>> arguments = new LinkedList<>();
		arguments.add(Flux.just(Val.of(1)));
		
		StepVerifier.create(mock.evaluate(Val.of(true), null, arguments))
			.expectError()
			.verify();
	}


	@Test
	void test_errorMessage() {
		Assertions.assertThat(mock.getErrorMessageForCurrentMode()).isNotEmpty();
	}
}
