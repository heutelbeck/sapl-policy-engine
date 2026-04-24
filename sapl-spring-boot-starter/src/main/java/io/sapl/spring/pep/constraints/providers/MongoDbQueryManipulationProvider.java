/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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
package io.sapl.spring.pep.constraints.providers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Query;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.spring.constraints.providers.ConstraintResponsibility;
import io.sapl.spring.pep.constraints.ConstraintHandler.Mapper;
import io.sapl.spring.pep.constraints.ConstraintHandlerProvider;
import io.sapl.spring.pep.constraints.Signal.MongoDbQueryShimSignal;
import io.sapl.spring.pep.constraints.ScopedConstraintHandler;
import io.sapl.spring.pep.constraints.SignalType;
import lombok.val;

/**
 * Translates a {@code mongo:queryManipulation} constraint into a
 * {@link Mapper} attached to the PEP's {@link MongoDbQueryShimSignal}. Each
 * condition string in the obligation is parsed as a MongoDB query fragment and
 * its key-value pairs are appended into the original {@link Query}'s BSON
 * document. If the original query already filters on a key the condition also
 * mentions, the condition value overrides it (mirrors the existing behaviour
 * of {@code QueryCreation.enforceQueryManipulation} in the legacy data
 * subtree).
 *
 * The obligation shape is
 *
 * <pre>{@code
 * {
 *   "type": "mongo:queryManipulation",
 *   "conditions": ["{'tenantId': 7}", "{'age': {'$gte': 18}}"]
 * }
 * }</pre>
 */
public class MongoDbQueryManipulationProvider implements ConstraintHandlerProvider {

    private static final String CONSTRAINT_TYPE  = "mongo:queryManipulation";
    private static final String FIELD_CONDITIONS = "conditions";
    private static final int    DEFAULT_PRIORITY = 30;

    @Override
    public Optional<ScopedConstraintHandler> getConstraintHandler(Value constraint, Set<SignalType> supportedSignals) {
        if (!ConstraintResponsibility.isResponsible(constraint, CONSTRAINT_TYPE)) {
            return Optional.empty();
        }
        if (!supportedSignals.contains(MongoDbQueryShimSignal.TYPE)) {
            return Optional.empty();
        }
        val conditions = extractConditions(constraint);
        if (conditions.isEmpty()) {
            return Optional.empty();
        }
        Mapper<Query> mapper = query -> appendConditions(query, conditions);
        return Optional.of(new ScopedConstraintHandler(mapper, MongoDbQueryShimSignal.TYPE, DEFAULT_PRIORITY));
    }

    private static List<String> extractConditions(Value constraint) {
        if (!(constraint instanceof ObjectValue object)) {
            return List.of();
        }
        if (!(object.get(FIELD_CONDITIONS) instanceof ArrayValue conditionsArray)) {
            return List.of();
        }
        val result = new ArrayList<String>(conditionsArray.size());
        for (val element : conditionsArray) {
            if (element instanceof TextValue(String text)) {
                result.add(text);
            }
        }
        return List.copyOf(result);
    }

    private static Query appendConditions(Query query, List<String> conditions) {
        val target = query.getQueryObject();
        for (val condition : conditions) {
            val parsed = new BasicQuery(condition).getQueryObject();
            parsed.forEach(target::append);
        }
        return query;
    }
}
