package io.sapl.spring.constraints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.google.common.base.Functions;

public class BundleUtilTests {

	private static final String TEST = "TEST";

	@Test
	@SuppressWarnings("unchecked")
	void when_providedAListOfConsumers_then_allConsumersAreCalledByConsumeAll() {
		Consumer<Object> consumer1          = mock(Consumer.class);
		Consumer<Object> consumer2          = mock(Consumer.class);
		var              consumers          = List.of(consumer1, consumer2);
		Consumer<Object> combindedConsumers = BundleUtil.consumeAll(consumers);
		combindedConsumers.accept(TEST);
		var inOrder = inOrder(consumer1, consumer2);
		inOrder.verify(consumer1, times(1)).accept(TEST);
		inOrder.verify(consumer2, times(1)).accept(TEST);
	}

	@Test
	void when_providedAListOfRunnable_then_allRunnablesAreRunByRunAll() {
		var runnable1          = mock(Runnable.class);
		var runnable2          = mock(Runnable.class);
		var runnables          = List.of(runnable1, runnable2);
		var combindedRunnables = BundleUtil.runAll(runnables);
		combindedRunnables.run();
		var inOrder = inOrder(runnable1, runnable2);
		inOrder.verify(runnable1, times(1)).run();
		inOrder.verify(runnable2, times(1)).run();
	}

	@Test
	void when_providedAListOfFunctions_then_allFunctionsAreCalledNEstedInOrderbyMapAll() {
		var function1       = spy(new Function<Integer, Integer>() {
								@Override
								public Integer apply(Integer t) {
									return t+1;
								}
							});
		var function2       = spy(new Function<Integer, Integer>() {
								@Override
								public Integer apply(Integer t) {
									return t * 2;
								}
							});
		var functions       = List.of(function1, function2);
		var nestedFunctions = BundleUtil.mapAll(functions);
		
		Function<Integer, Integer> nestedManual= x -> function2.apply(function1.apply((Integer) Functions.identity().apply(x)));
		
		
		var result          = nestedFunctions.apply(100);
		assertEquals(202, result);
		var inOrder = inOrder(function1, function2);
		inOrder.verify(function1, times(1)).apply(100);
		inOrder.verify(function2, times(1)).apply(101);
		
		
		
	}
}
