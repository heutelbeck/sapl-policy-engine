package io.sapl.prp.inmemory.indexed.improved;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.prp.inmemory.indexed.Bitmask;
import io.sapl.prp.inmemory.indexed.Bool;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Getter
public class Predicate {

	private final Bool bool;

	private final Bitmask conjunctions = new Bitmask();

	private final Bitmask falseForTruePredicate = new Bitmask();

	private final Bitmask falseForFalsePredicate = new Bitmask();

	public Predicate(final Bool bool) {
		this.bool = Preconditions.checkNotNull(bool);
	}

	public Mono<Boolean> evaluate(EvaluationContext subscriptionScopedEvaluationCtx) {
		try {
			return getBool().evaluate(subscriptionScopedEvaluationCtx);
		} catch (PolicyEvaluationException e) {
			log.debug(Throwables.getStackTraceAsString(e));
			return Mono.error(e);
		}
	}

}
