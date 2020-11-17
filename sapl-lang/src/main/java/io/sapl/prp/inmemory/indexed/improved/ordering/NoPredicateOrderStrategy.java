package io.sapl.prp.inmemory.indexed.improved.ordering;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import io.sapl.prp.inmemory.indexed.improved.Predicate;
import io.sapl.prp.inmemory.indexed.improved.PredicateInfo;

public class NoPredicateOrderStrategy implements PredicateOrderStrategy {
	@Override
	public List<Predicate> createPredicateOrder(Collection<PredicateInfo> predicateInfos) {
		return predicateInfos.stream().map(PredicateInfo::getPredicate).collect(Collectors.toList());
	}
}
