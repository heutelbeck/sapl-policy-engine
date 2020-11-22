package io.sapl.prp.inmemory.indexed.improved.ordering;

import io.sapl.prp.inmemory.indexed.improved.Predicate;
import io.sapl.prp.inmemory.indexed.improved.PredicateInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class DefaultPredicateOrderStrategy implements PredicateOrderStrategy {

    @Override
    public List<Predicate> createPredicateOrder(Collection<PredicateInfo> data) {
        List<PredicateInfo> predicateInfos = new ArrayList<>(data);
        predicateInfos.parallelStream().forEach(predicateInfo -> predicateInfo.setScore(createScore(predicateInfo)));

        return predicateInfos.stream().sorted(Collections.reverseOrder()).map(PredicateInfo::getPredicate)
                .collect(Collectors.toList());
    }

    private double createScore(final PredicateInfo predicateInfo) {
        var square = 2.0;
        var groupedPositives = predicateInfo.getGroupedNumberOfPositives();
        var groupedNegatives = predicateInfo.getGroupedNumberOfNegatives();
        var relevance = predicateInfo.getRelevance();
        var costs = 1.0;

        return Math.pow(relevance, square - relevance) * (groupedPositives + groupedNegatives) / costs * (square
                - Math.pow((groupedPositives - groupedNegatives) / (groupedPositives + groupedNegatives), square));
    }

}
