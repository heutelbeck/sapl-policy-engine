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
