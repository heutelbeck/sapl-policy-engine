package io.sapl.prp.inmemory.indexed.improved.ordering;

import io.sapl.prp.inmemory.indexed.improved.Predicate;
import io.sapl.prp.inmemory.indexed.improved.PredicateInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ExistingOrderStrategy implements PredicateOrderStrategy {

    @Override
    public List<Predicate> createPredicateOrder(Collection<PredicateInfo> data) {
        List<PredicateInfo> predicateInfos = new ArrayList<>(data);
        for (PredicateInfo predicateInfo : predicateInfos) {
            predicateInfo.setScore(createScore(predicateInfo));

        }
//        Collections.sort(predicateInfos, Collections.reverseOrder());

        return predicateInfos.stream().sorted(Collections.reverseOrder()).map(PredicateInfo::getPredicate)
                .collect(Collectors.toList());
    }

    private static double createScore(final PredicateInfo predicateInfo) {
        final double square = 2.0;
        final double groupedPositives = predicateInfo.getGroupedNumberOfPositives();
        final double groupedNegatives = predicateInfo.getGroupedNumberOfNegatives();
        final double relevance = predicateInfo.getRelevance();
        final double costs = 1.0;

        return Math.pow(relevance, square - relevance) * (groupedPositives + groupedNegatives) / costs * (square
                - Math.pow((groupedPositives - groupedNegatives) / (groupedPositives + groupedNegatives), square));
    }

}
