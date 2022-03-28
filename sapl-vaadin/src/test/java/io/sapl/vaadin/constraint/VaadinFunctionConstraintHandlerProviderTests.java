package io.sapl.vaadin.constraint;

import static com.helger.commons.mock.CommonsAssert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.component.UI;

import reactor.core.publisher.Mono;

class VaadinFunctionConstraintHandlerProviderTests {

	@Test
	void when_VaadinFunctionConstraintHandlerProviderWithPredicateAndFunctionIsResponsibleIsCalled_then_PredicateTestWithJsonNodeIsCalled() {
		// GIVEN
		@SuppressWarnings("unchecked")
		Predicate<JsonNode> predicate = (Predicate<JsonNode>) mock(Predicate.class);
		@SuppressWarnings("unchecked")
		Function<UI, Mono<Boolean>> function = (Function<UI, Mono<Boolean>>) mock(Function.class);

		JsonNode jsonNode = mock(JsonNode.class);

		VaadinFunctionConstraintHandlerProvider vaadinFunctionConstraintHandlerProvider = VaadinFunctionConstraintHandlerProvider.of(predicate, function);
		// WHEN
		vaadinFunctionConstraintHandlerProvider.isResponsible(jsonNode);

		// THEN
		verify(predicate).test(jsonNode);
	}

	@Test
	void when_VaadinFunctionConstraintHandlerProviderWithPredicateAndFunctionGetHandlerIsCalled_then_FunctionIsReturned() {
		// GIVEN
		@SuppressWarnings("unchecked")
		Predicate<JsonNode> predicate = (Predicate<JsonNode>) mock(Predicate.class);
		@SuppressWarnings("unchecked")
		Function<UI, Mono<Boolean>> function = (Function<UI, Mono<Boolean>>) mock(Function.class);

		VaadinFunctionConstraintHandlerProvider vaadinFunctionConstraintHandlerProvider = VaadinFunctionConstraintHandlerProvider.of(predicate, function);
		// WHEN+THEN
		assertEquals(function, vaadinFunctionConstraintHandlerProvider.getHandler(mock(JsonNode.class)));
	}

	@Test
	void when_VaadinFunctionConstraintHandlerProviderWithJsonNodeAndConsumerAndEmptyFilterIsResponsibleIsCalled_then_TrueIsReturned() {
		// GIVEN
		@SuppressWarnings("unchecked")
		Consumer<JsonNode> consumer = (Consumer<JsonNode>) mock(Consumer.class);
		JsonNode jsonNode = mock(JsonNode.class);
		@SuppressWarnings("unchecked")
		Iterator<String> iter = (Iterator<String>)mock(Iterator.class);
		when(jsonNode.fieldNames()).thenReturn(iter);

		VaadinFunctionConstraintHandlerProvider vaadinFunctionConstraintHandlerProvider =
				VaadinFunctionConstraintHandlerProvider.of(jsonNode, consumer);
		// WHEN + THEN
		assertTrue(vaadinFunctionConstraintHandlerProvider.isResponsible(jsonNode));
	}

	@Test
	void when_VaadinFunctionConstraintHandlerProviderWithJsonNodeAndConsumerIsResponsibleIsCalledWithFilterFieldInConstraint_then_TrueIsReturned() {
		// GIVEN
		@SuppressWarnings("unchecked")
		Consumer<JsonNode> consumer = (Consumer<JsonNode>) mock(Consumer.class);
		JsonNode constraintFilter = mock(JsonNode.class);
		@SuppressWarnings("unchecked")
		Iterator<String> iterator = (Iterator<String>)mock(Iterator.class);
		when(constraintFilter.fieldNames()).thenReturn(iterator);

		when(iterator.hasNext()).thenAnswer(new Answer<Boolean>() {
			boolean trueOnce = true; // Return True only the first time hasNext() is called to prevent deadlock
			@Override
			public Boolean answer(InvocationOnMock invocation) {
				boolean tmp = trueOnce;
				trueOnce = false;
				return tmp;
			}
		});
		JsonNode constraint = mock(JsonNode.class);
		when(constraint.has(any())).thenReturn(true);
		when(constraint.get(any())).thenReturn(constraint);
		when(constraintFilter.get(any())).thenReturn(constraint);

		VaadinFunctionConstraintHandlerProvider vaadinFunctionConstraintHandlerProvider =
				VaadinFunctionConstraintHandlerProvider.of(constraintFilter, consumer);
		// WHEN + THEN
		assertTrue(vaadinFunctionConstraintHandlerProvider.isResponsible(constraint));
	}

	@Test
	void when_VaadinFunctionConstraintHandlerProviderWithJsonNodeAndConsumerIsResponsibleIsCalledWithFilterFieldNotMatching_then_FalseIsReturned() {
		// GIVEN
		@SuppressWarnings("unchecked")
		Consumer<JsonNode> consumer = (Consumer<JsonNode>) mock(Consumer.class);
		JsonNode constraintFilter = mock(JsonNode.class);
		@SuppressWarnings("unchecked")
		Iterator<String> iterator = (Iterator<String>)mock(Iterator.class);
		when(constraintFilter.fieldNames()).thenReturn(iterator);

		when(iterator.hasNext()).thenAnswer(new Answer<Boolean>() {
			boolean trueOnce = true; // Return True only the first time hasNext() is called to prevent deadlock
			@Override
			public Boolean answer(InvocationOnMock invocation) {
				boolean tmp = trueOnce;
				trueOnce = false;
				return tmp;
			}
		});
		JsonNode constraint = mock(JsonNode.class);
		when(constraint.has(any())).thenReturn(true);
		when(constraint.get(any())).thenReturn(constraint);

		VaadinFunctionConstraintHandlerProvider vaadinFunctionConstraintHandlerProvider =
				VaadinFunctionConstraintHandlerProvider.of(constraintFilter, consumer);
		// WHEN + THEN
		assertFalse(vaadinFunctionConstraintHandlerProvider.isResponsible(constraint));
	}

	@Test
	void when_VaadinFunctionConstraintHandlerProviderWithJsonNodeAndConsumerIsResponsibleIsCalledWithFilterFieldNotInConstraintAndNotMatching_then_FalseIsReturned() {
		// GIVEN
		@SuppressWarnings("unchecked")
		Consumer<JsonNode> consumer = (Consumer<JsonNode>) mock(Consumer.class);
		JsonNode constraintFilter = mock(JsonNode.class);
		@SuppressWarnings("unchecked")
		Iterator<String> iterator = (Iterator<String>)mock(Iterator.class);
		when(constraintFilter.fieldNames()).thenReturn(iterator);

		when(iterator.hasNext()).thenAnswer(new Answer<Boolean>() {
			boolean trueOnce = true; // Return True only the first time hasNext() is called to prevent deadlock
			@Override
			public Boolean answer(InvocationOnMock invocation) {
				boolean tmp = trueOnce;
				trueOnce = false;
				return tmp;
			}
		});
		JsonNode constraint = mock(JsonNode.class);
		when(constraint.has(any())).thenReturn(false);
		when(constraint.get(any())).thenReturn(constraint);

		VaadinFunctionConstraintHandlerProvider vaadinFunctionConstraintHandlerProvider =
				VaadinFunctionConstraintHandlerProvider.of(constraintFilter, consumer);
		// WHEN + THEN
		assertFalse(vaadinFunctionConstraintHandlerProvider.isResponsible(constraint));
	}

	@Test
	void when_VaadinFunctionConstraintHandlerProviderWithJsonNodeAndConsumerIsResponsibleIsCalledWithFilterFieldNotInConstraintAndMatching_then_FalseIsReturned() {
		// GIVEN
		@SuppressWarnings("unchecked")
		Consumer<JsonNode> consumer = (Consumer<JsonNode>) mock(Consumer.class);
		JsonNode constraintFilter = mock(JsonNode.class);
		@SuppressWarnings("unchecked")
		Iterator<String> iterator = (Iterator<String>)mock(Iterator.class);
		when(constraintFilter.fieldNames()).thenReturn(iterator);

		when(iterator.hasNext()).thenAnswer(new Answer<Boolean>() {
			boolean trueOnce = true; // Return True only the first time hasNext() is called to prevent deadlock
			@Override
			public Boolean answer(InvocationOnMock invocation) {
				boolean tmp = trueOnce;
				trueOnce = false;
				return tmp;
			}
		});
		JsonNode constraint = mock(JsonNode.class);
		when(constraint.has(any())).thenReturn(false);
		when(constraint.get(any())).thenReturn(constraint);
		when(constraintFilter.get(any())).thenReturn(constraint);

		VaadinFunctionConstraintHandlerProvider vaadinFunctionConstraintHandlerProvider =
				VaadinFunctionConstraintHandlerProvider.of(constraintFilter, consumer);
		// WHEN + THEN
		assertFalse(vaadinFunctionConstraintHandlerProvider.isResponsible(constraint));
	}

	@Test
	void when_VaadinFunctionConstraintHandlerProviderWithJsonNodeAndConsumerGetHandlerIsCalled_then_FunctionThatCallsHandlerIsReturned() {
		// GIVEN
		@SuppressWarnings("unchecked")
		Consumer<JsonNode> consumer = (Consumer<JsonNode>) mock(Consumer.class);
		JsonNode constraint = mock(JsonNode.class);

		VaadinFunctionConstraintHandlerProvider vaadinFunctionConstraintHandlerProvider =
				VaadinFunctionConstraintHandlerProvider.of(mock(JsonNode.class), consumer);
		// WHEN
		vaadinFunctionConstraintHandlerProvider.getHandler(constraint).apply(mock(UI.class));

		// THEN
		verify(consumer).accept(constraint);
	}

	@Test
	void when_VaadinFunctionConstraintHandlerProviderWithJsonNodeAndFunctionIsResponsibleIsCalledWithFilterFieldInConstraint_then_TrueIsReturned() {
		// GIVEN
		@SuppressWarnings("unchecked")
		Function<JsonNode, Mono<Boolean>> function = (Function<JsonNode, Mono<Boolean>>) mock(Function.class);
		JsonNode constraintFilter = mock(JsonNode.class);
		@SuppressWarnings("unchecked")
		Iterator<String> iterator = (Iterator<String>)mock(Iterator.class);
		when(constraintFilter.fieldNames()).thenReturn(iterator);

		when(iterator.hasNext()).thenAnswer(new Answer<Boolean>() {
			boolean trueOnce = true; // Return True only the first time hasNext() is called to prevent deadlock
			@Override
			public Boolean answer(InvocationOnMock invocation) {
				boolean tmp = trueOnce;
				trueOnce = false;
				return tmp;
			}
		});
		JsonNode constraint = mock(JsonNode.class);
		when(constraint.has(any())).thenReturn(true);
		when(constraint.get(any())).thenReturn(constraint);
		when(constraintFilter.get(any())).thenReturn(constraint);

		VaadinFunctionConstraintHandlerProvider vaadinFunctionConstraintHandlerProvider =
				VaadinFunctionConstraintHandlerProvider.of(constraintFilter, function);
		// WHEN + THEN
		assertTrue(vaadinFunctionConstraintHandlerProvider.isResponsible(constraint));
	}

	@Test
	void when_VaadinFunctionConstraintHandlerProviderWithJsonNodeAndFunctionIsResponsibleIsCalledWithFilterFieldNotMatching_then_FalseIsReturned() {
		// GIVEN
		@SuppressWarnings("unchecked")
		Function<JsonNode, Mono<Boolean>> function = (Function<JsonNode, Mono<Boolean>>) mock(Function.class);
		JsonNode constraintFilter = mock(JsonNode.class);
		@SuppressWarnings("unchecked")
		Iterator<String> iterator = (Iterator<String>)mock(Iterator.class);
		when(constraintFilter.fieldNames()).thenReturn(iterator);

		when(iterator.hasNext()).thenAnswer(new Answer<Boolean>() {
			boolean trueOnce = true; // Return True only the first time hasNext() is called to prevent deadlock
			@Override
			public Boolean answer(InvocationOnMock invocation) {
				boolean tmp = trueOnce;
				trueOnce = false;
				return tmp;
			}
		});
		JsonNode constraint = mock(JsonNode.class);
		when(constraint.has(any())).thenReturn(true);
		when(constraint.get(any())).thenReturn(constraint);

		VaadinFunctionConstraintHandlerProvider vaadinFunctionConstraintHandlerProvider =
				VaadinFunctionConstraintHandlerProvider.of(constraintFilter, function);
		// WHEN + THEN
		assertFalse(vaadinFunctionConstraintHandlerProvider.isResponsible(constraint));
	}

	@Test
	void when_VaadinFunctionConstraintHandlerProviderWithJsonNodeAndFunctionIsResponsibleIsCalledWithFilterFieldNotInConstraintAndNotMatching_then_FalseIsReturned() {
		// GIVEN
		@SuppressWarnings("unchecked")
		Function<JsonNode, Mono<Boolean>> function = (Function<JsonNode, Mono<Boolean>>) mock(Function.class);
		JsonNode constraintFilter = mock(JsonNode.class);
		@SuppressWarnings("unchecked")
		Iterator<String> iterator = (Iterator<String>)mock(Iterator.class);
		when(constraintFilter.fieldNames()).thenReturn(iterator);

		when(iterator.hasNext()).thenAnswer(new Answer<Boolean>() {
			boolean trueOnce = true; // Return True only the first time hasNext() is called to prevent deadlock
			@Override
			public Boolean answer(InvocationOnMock invocation) {
				boolean tmp = trueOnce;
				trueOnce = false;
				return tmp;
			}
		});
		JsonNode constraint = mock(JsonNode.class);
		when(constraint.has(any())).thenReturn(false);
		when(constraint.get(any())).thenReturn(constraint);

		VaadinFunctionConstraintHandlerProvider vaadinFunctionConstraintHandlerProvider =
				VaadinFunctionConstraintHandlerProvider.of(constraintFilter, function);
		// WHEN + THEN
		assertFalse(vaadinFunctionConstraintHandlerProvider.isResponsible(constraint));
	}

	@Test
	void when_VaadinFunctionConstraintHandlerProviderWithJsonNodeAndFunctionIsResponsibleIsCalledWithFilterFieldNotInConstraintAndMatching_then_FalseIsReturned() {
		// GIVEN
		@SuppressWarnings("unchecked")
		Function<JsonNode, Mono<Boolean>> function = (Function<JsonNode, Mono<Boolean>>) mock(Function.class);
		JsonNode constraintFilter = mock(JsonNode.class);
		@SuppressWarnings("unchecked")
		Iterator<String> iterator = (Iterator<String>)mock(Iterator.class);
		when(constraintFilter.fieldNames()).thenReturn(iterator);

		when(iterator.hasNext()).thenAnswer(new Answer<Boolean>() {
			boolean trueOnce = true; // Return True only the first time hasNext() is called to prevent deadlock
			@Override
			public Boolean answer(InvocationOnMock invocation) {
				boolean tmp = trueOnce;
				trueOnce = false;
				return tmp;
			}
		});
		JsonNode constraint = mock(JsonNode.class);
		when(constraint.has(any())).thenReturn(false);
		when(constraint.get(any())).thenReturn(constraint);
		when(constraintFilter.get(any())).thenReturn(constraint);

		VaadinFunctionConstraintHandlerProvider vaadinFunctionConstraintHandlerProvider =
				VaadinFunctionConstraintHandlerProvider.of(constraintFilter, function);
		// WHEN + THEN
		assertFalse(vaadinFunctionConstraintHandlerProvider.isResponsible(constraint));
	}

	@Test
	void when_VaadinFunctionConstraintHandlerProviderWithJsonNodeAndFunctionGetHandlerIsCalled_then_FunctionThatCallsHandlerIsReturned() {
		// GIVEN
		@SuppressWarnings("unchecked")
		Function<JsonNode, Mono<Boolean>> function = (Function<JsonNode, Mono<Boolean>>) mock(Function.class);
		JsonNode constraint = mock(JsonNode.class);

		VaadinFunctionConstraintHandlerProvider vaadinFunctionConstraintHandlerProvider =
				VaadinFunctionConstraintHandlerProvider.of(mock(JsonNode.class), function);
		// WHEN
		vaadinFunctionConstraintHandlerProvider.getHandler(constraint).apply(mock(UI.class));

		// THEN
		verify(function).apply(constraint);
	}

}