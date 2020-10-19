package io.sapl.prp.inmemory.indexed.improved.ordering;

import io.sapl.prp.inmemory.indexed.improved.Predicate;
import io.sapl.prp.inmemory.indexed.improved.PredicateInfo;

import java.util.Collection;
import java.util.List;

public interface PredicateOrderStrategy {

	List<Predicate> createPredicateOrder(final Collection<PredicateInfo> data);

}
