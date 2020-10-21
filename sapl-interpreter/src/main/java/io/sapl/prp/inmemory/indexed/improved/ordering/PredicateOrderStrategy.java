package io.sapl.prp.inmemory.indexed.improved.ordering;

import java.util.Collection;
import java.util.List;

import io.sapl.prp.inmemory.indexed.improved.Predicate;
import io.sapl.prp.inmemory.indexed.improved.PredicateInfo;

public interface PredicateOrderStrategy {

	List<Predicate> createPredicateOrder(final Collection<PredicateInfo> data);

}
