package io.sapl.prp.inmemory.indexed.improved;

import com.google.common.base.Preconditions;

import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.prp.inmemory.indexed.Bitmask;
import io.sapl.prp.inmemory.indexed.Bool;
import lombok.Getter;
import reactor.core.publisher.Mono;

@Getter
public class Predicate {

	private final Bool bool;
	private final Bitmask conjunctions = new Bitmask();
	private final Bitmask falseForTruePredicate = new Bitmask();
	private final Bitmask falseForFalsePredicate = new Bitmask();

	public Predicate(final Bool bool) {
		this.bool = Preconditions.checkNotNull(bool);
	}

	public Mono<Val> evaluate(EvaluationContext subscriptionScopedEvaluationCtx) {
		return getBool().evaluate(subscriptionScopedEvaluationCtx);
	}

}
