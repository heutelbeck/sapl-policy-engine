/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.springdatacommon.services;

import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.spring.constraints.providers.ConstraintResponsibility;
import org.springframework.security.access.AccessDeniedException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class ConstraintQueryEnforcementService {

    private static final JsonSchemaFactory SCHEMA_FACTORY = JsonSchemaFactory
            .getInstance(SpecVersion.VersionFlag.V201909);

    public QueryManipulationConstraintHandlerService queryManipulationForMongoReactive(AuthorizationDecision decision) {

        var obligations        = new HashSet<>(decision.obligations());
        var handledObligations = new HashSet<Value>();

        var bundle = new QueryManipulationConstraintHandlerService(
                filterHandlerType(obligations, handledObligations, ConstraintHandlerType.MONGO_QUERY_MANIPULATION));

        obligations.removeIf(handledObligations::contains);

        if (!obligations.isEmpty()) {
            throw getAccessDeniedException(obligations);
        }

        return bundle;
    }

    public QueryManipulationConstraintHandlerService queryManipulationForR2dbc(AuthorizationDecision decision) {

        var obligations        = new HashSet<>(decision.obligations());
        var handledObligations = new HashSet<Value>();

        var bundle = new QueryManipulationConstraintHandlerService(
                filterHandlerType(obligations, handledObligations, ConstraintHandlerType.R2DBC_QUERY_MANIPULATION));

        obligations.removeIf(handledObligations::contains);

        if (!obligations.isEmpty()) {
            throw getAccessDeniedException(obligations);
        }

        return bundle;
    }

    private List<RecordConstraintData> filterHandlerType(HashSet<Value> obligations, HashSet<Value> handledObligations,
            ConstraintHandlerType type) {
        var constraintDataRecords = new ArrayList<RecordConstraintData>();

        for (var obligation : obligations) {

            if (ConstraintResponsibility.isResponsible(obligation, type.getType())) {
                var jsonObligation = ValueJsonMarshaller.toJsonNode(obligation);
                var schema         = SCHEMA_FACTORY.getSchema(type.getTemplate());
                var errors         = schema.validate(jsonObligation);

                if (errors.isEmpty()) {
                    constraintDataRecords.add(new RecordConstraintData(type, jsonObligation));
                    handledObligations.add(obligation);
                }
            }
        }

        return constraintDataRecords;
    }

    private AccessDeniedException getAccessDeniedException(Iterable<Value> unhandledObligations) {

        var messageBuilder = new StringBuilder();

        for (var unhandableObligation : unhandledObligations) {
            messageBuilder.append("Unhandable Obligation: ")
                    .append(ValueJsonMarshaller.toPrettyString(unhandableObligation)).append('\n');
        }

        return new AccessDeniedException(messageBuilder.toString());
    }

}
