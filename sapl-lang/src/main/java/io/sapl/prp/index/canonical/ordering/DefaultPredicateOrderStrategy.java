/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.prp.index.canonical.ordering;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import io.sapl.prp.index.canonical.Predicate;
import io.sapl.prp.index.canonical.PredicateInfo;

public class DefaultPredicateOrderStrategy implements PredicateOrderStrategy {

    @Override
    public List<Predicate> createPredicateOrder(Collection<PredicateInfo> data) {
        List<PredicateInfo> predicateInfos = new ArrayList<>(data);
        predicateInfos.parallelStream().forEach(predicateInfo -> predicateInfo.setScore(createScore(predicateInfo)));

        return predicateInfos.stream().sorted(Collections.reverseOrder()).map(PredicateInfo::getPredicate)
                .collect(Collectors.toList());
    }

    private double createScore(final PredicateInfo predicateInfo) {
        var square = 2.0D;
        var groupedPositives = predicateInfo.getGroupedNumberOfPositives();
        var groupedNegatives = predicateInfo.getGroupedNumberOfNegatives();
        var relevance = predicateInfo.getRelevance();
        var costs = 1.0D;

        return Math.pow(relevance, square - relevance) * (groupedPositives + groupedNegatives) / costs * (square
                - Math.pow(((double) groupedPositives - (double) groupedNegatives) / ((double) groupedPositives + (double) groupedNegatives), square));
    }

}
