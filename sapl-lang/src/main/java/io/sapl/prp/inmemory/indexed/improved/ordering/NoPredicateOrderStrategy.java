package io.sapl.prp.inmemory.indexed.improved.ordering;

import io.sapl.prp.inmemory.indexed.improved.Predicate;
import io.sapl.prp.inmemory.indexed.improved.PredicateInfo;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class NoPredicateOrderStrategy implements PredicateOrderStrategy {
    @Override
    public List<Predicate> createPredicateOrder(Collection<PredicateInfo> predicateInfos) {
        return predicateInfos.stream().map(PredicateInfo::getPredicate).collect(Collectors.toList());
    }
}
