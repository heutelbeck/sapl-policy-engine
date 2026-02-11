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
package io.sapl.pdp.interceptors;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.AttributeRecord;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.compiler.document.Vote;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.List;
import java.util.function.Function;

/**
 * Utility class for building reports from Vote decisions.
 */
@UtilityClass
public class ReportBuilderUtil {

    // Field name constants (alphabetical)
    private static final String FIELD_ACTION                     = "action";
    private static final String FIELD_ADVICE                     = "advice";
    private static final String FIELD_ALGORITHM                  = "algorithm";
    private static final String FIELD_ATTRIBUTE_NAME             = "attributeName";
    private static final String FIELD_ATTRIBUTES                 = "attributes";
    private static final String FIELD_AUTHORIZATION_SUBSCRIPTION = "authorizationSubscription";
    private static final String FIELD_CONFIGURATION_ID           = "configurationId";
    private static final String FIELD_CONTRIBUTING_DOCUMENTS     = "contributingDocuments";
    private static final String FIELD_DECISION                   = "decision";
    private static final String FIELD_ENVIRONMENT                = "environment";
    private static final String FIELD_ERRORS                     = "errors";
    private static final String FIELD_LINE                       = "line";
    private static final String FIELD_MESSAGE                    = "message";
    private static final String FIELD_NAME                       = "name";
    private static final String FIELD_OBLIGATIONS                = "obligations";
    private static final String FIELD_PDP_ID                     = "pdpId";
    private static final String FIELD_RESOURCE                   = "resource";
    private static final String FIELD_RETRIEVED_AT               = "retrievedAt";
    private static final String FIELD_SUBJECT                    = "subject";
    private static final String FIELD_SUBSCRIPTION_ID            = "subscriptionId";
    private static final String FIELD_TIMESTAMP                  = "timestamp";
    private static final String FIELD_VALUE                      = "value";
    private static final String FIELD_VOTER_NAME                 = "voterName";

    public static VoteReport extractReport(Vote vote, String timestamp, String subscriptionId,
            AuthorizationSubscription authorizationSubscription) {
        return VoteReport.from(vote, timestamp, subscriptionId, authorizationSubscription);
    }

    public static ObjectValue extractReportAsValue(Vote vote, String timestamp, String subscriptionId,
            AuthorizationSubscription authorizationSubscription) {
        return toObjectValue(VoteReport.from(vote, timestamp, subscriptionId, authorizationSubscription));
    }

    public static ObjectValue toObjectValue(VoteReport report) {
        val builder = ObjectValue.builder()
                .put(FIELD_TIMESTAMP, Value.of(ReportTextRenderUtil.formatTimestamp(report.timestamp())))
                .put(FIELD_SUBSCRIPTION_ID, Value.of(report.subscriptionId()))
                .put(FIELD_AUTHORIZATION_SUBSCRIPTION, subscriptionToValue(report.authorizationSubscription()))
                .put(FIELD_DECISION, Value.of(report.decision().name()))
                .put(FIELD_VOTER_NAME, Value.of(report.voterName())).put(FIELD_PDP_ID, Value.of(report.pdpId()))
                .put(FIELD_CONFIGURATION_ID, Value.of(report.configurationId()));

        putIfNonEmpty(builder, FIELD_OBLIGATIONS, report.obligations());
        putIfNonEmpty(builder, FIELD_ADVICE, report.advice());
        putIfDefined(builder, FIELD_RESOURCE, report.resource());

        if (report.algorithm() != null) {
            builder.put(FIELD_ALGORITHM, Value.of(report.algorithm().votingMode().name()));
        }

        putArray(builder, FIELD_CONTRIBUTING_DOCUMENTS, report.contributingDocuments(),
                ReportBuilderUtil::documentToValue);
        putArray(builder, FIELD_ERRORS, report.errors(), ReportBuilderUtil::errorToValue);

        return builder.build();
    }

    private static ObjectValue documentToValue(ContributingDocument doc) {
        val builder = ObjectValue.builder().put(FIELD_NAME, Value.of(doc.name())).put(FIELD_DECISION,
                Value.of(doc.decision().name()));
        putArray(builder, FIELD_ATTRIBUTES, doc.attributes(), ReportBuilderUtil::attributeToValue);
        putArray(builder, FIELD_ERRORS, doc.errors(), ReportBuilderUtil::errorToValue);
        return builder.build();
    }

    private static ObjectValue errorToValue(ErrorValue error) {
        val builder = ObjectValue.builder().put(FIELD_MESSAGE, Value.of(error.message()));
        if (error.location() != null) {
            builder.put(FIELD_LINE, Value.of(error.location().line()));
        }
        return builder.build();
    }

    private static ObjectValue attributeToValue(AttributeRecord attr) {
        return ObjectValue.builder().put(FIELD_ATTRIBUTE_NAME, Value.of(attr.invocation().attributeName()))
                .put(FIELD_VALUE, attr.attributeValue())
                .put(FIELD_RETRIEVED_AT, Value.of(attr.retrievedAt().toString())).build();
    }

    private static ObjectValue subscriptionToValue(AuthorizationSubscription subscription) {
        val builder = ObjectValue.builder().put(FIELD_SUBJECT, subscription.subject())
                .put(FIELD_ACTION, subscription.action()).put(FIELD_RESOURCE, subscription.resource());
        putIfDefined(builder, FIELD_ENVIRONMENT, subscription.environment());
        return builder.build();
    }

    private static void putIfNonEmpty(ObjectValue.Builder builder, String key, ArrayValue value) {
        if (value != null && !value.isEmpty()) {
            builder.put(key, value);
        }
    }

    private static void putIfDefined(ObjectValue.Builder builder, String key, Value value) {
        if (value != null && !(value instanceof UndefinedValue)) {
            builder.put(key, value);
        }
    }

    private static <T> void putArray(ObjectValue.Builder builder, String key, List<T> items,
            Function<T, Value> mapper) {
        if (items != null && !items.isEmpty()) {
            builder.put(key, ArrayValue.builder().addAll(items.stream().map(mapper).toList()).build());
        }
    }
}
