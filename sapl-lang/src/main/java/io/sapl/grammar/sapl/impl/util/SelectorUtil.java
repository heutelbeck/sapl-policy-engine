package io.sapl.grammar.sapl.impl.util;

import java.util.function.BiFunction;
import java.util.function.Supplier;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.context.AuthorizationContext;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;

@UtilityClass
public class SelectorUtil {
	public static Supplier<Flux<Val>> toArrayElementSelector(BiFunction<Integer, Val, Boolean> selector) {
		return () -> Flux.deferContextual(ctx -> {
			var relativeNode = AuthorizationContext.getRelativeNode(ctx);
			var index        = AuthorizationContext.getIndex(ctx);
			try {
				return Flux.just(Val.of(selector.apply(index, relativeNode)));
			} catch (PolicyEvaluationException e) {
				return Flux.just(Val.error(e.getMessage()));
			}
		});
	}

	public static Supplier<Flux<Val>> toObjectFieldSelector(BiFunction<String, Val, Boolean> selector) {
		return () -> Flux.deferContextual(ctx -> {			
			var relativeNode = AuthorizationContext.getRelativeNode(ctx);
			System.out.println("rela: "+relativeNode);
			var key          = AuthorizationContext.getKey(ctx);
			try {
				return Flux.just(Val.of(selector.apply(key, relativeNode)));
			} catch (PolicyEvaluationException e) {
				return Flux.just(Val.error(e.getMessage()));
			}
		});
	}

}
