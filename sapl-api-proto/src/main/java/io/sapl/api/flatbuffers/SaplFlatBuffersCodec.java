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
package io.sapl.api.flatbuffers;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.flatbuffers.FlatBufferBuilder;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.NullValue;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.IdentifiableAuthorizationSubscription;
import io.sapl.api.pdp.MultiAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import lombok.experimental.UtilityClass;

/**
 * Direct FlatBuffers wire format codec for SAPL domain types.
 * <p>
 * Serializes/deserializes directly to/from domain objects without intermediate
 * generated FlatBuffers classes. Wire format is compatible with the .fbs schema
 * definitions in src/main/flatbuffers/.
 */
@UtilityClass
public class SaplFlatBuffersCodec {

    // ValueUnion type codes (matching sapl_types.fbs)
    private static final byte VALUE_NONE      = 0;
    private static final byte VALUE_NULL      = 1;
    private static final byte VALUE_BOOL      = 2;
    private static final byte VALUE_NUMBER    = 3;
    private static final byte VALUE_TEXT      = 4;
    private static final byte VALUE_ARRAY     = 5;
    private static final byte VALUE_OBJECT    = 6;
    private static final byte VALUE_UNDEFINED = 7;
    private static final byte VALUE_ERROR     = 8;

    // Decision enum values
    private static final byte DECISION_INDETERMINATE  = 0;
    private static final byte DECISION_PERMIT         = 1;
    private static final byte DECISION_DENY           = 2;
    private static final byte DECISION_NOT_APPLICABLE = 3;

    /**
     * Serializes a Value to FlatBuffers bytes.
     *
     * @param value the value to serialize
     * @return FlatBuffers-encoded bytes
     */
    public static byte[] writeValue(Value value) {
        FlatBufferBuilder builder     = new FlatBufferBuilder(256);
        int               valueOffset = createValue(builder, value);
        builder.finish(valueOffset);
        return builder.sizedByteArray();
    }

    /**
     * Deserializes a Value from FlatBuffers bytes.
     *
     * @param bytes FlatBuffers-encoded bytes
     * @return the deserialized Value
     */
    public static Value readValue(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        FbValue    table  = new FbValue().__assign(buffer.getInt(buffer.position()) + buffer.position(), buffer);
        return readValue(table);
    }

    private static int createValue(FlatBufferBuilder builder, Value value) {
        byte kindType;
        int  kindOffset;
        switch (value) {
        case NullValue ignored         -> {
            kindType   = VALUE_NULL;
            kindOffset = FbNullValue.createFbNullValue(builder);
        }
        case BooleanValue(boolean b)   -> {
            kindType   = VALUE_BOOL;
            kindOffset = FbBoolValue.createFbBoolValue(builder, b);
        }
        case NumberValue(BigDecimal n) -> {
            kindType = VALUE_NUMBER;
            int strOffset = builder.createString(n.toPlainString());
            kindOffset = FbNumberValue.createFbNumberValue(builder, strOffset);
        }
        case TextValue(String s)       -> {
            kindType = VALUE_TEXT;
            int strOffset = builder.createString(s);
            kindOffset = FbTextValue.createFbTextValue(builder, strOffset);
        }
        case ArrayValue arr            -> {
            kindType   = VALUE_ARRAY;
            kindOffset = createArrayValue(builder, arr);
        }
        case ObjectValue obj           -> {
            kindType   = VALUE_OBJECT;
            kindOffset = createObjectValue(builder, obj);
        }
        case UndefinedValue ignored    -> {
            kindType   = VALUE_UNDEFINED;
            kindOffset = FbUndefinedValue.createFbUndefinedValue(builder);
        }
        case ErrorValue err            -> {
            kindType = VALUE_ERROR;
            int msgOffset = builder.createString(err.message());
            kindOffset = FbErrorValue.createFbErrorValue(builder, msgOffset, 0);
        }
        }
        FbValue.startFbValue(builder);
        FbValue.addKindType(builder, kindType);
        FbValue.addKind(builder, kindOffset);
        return FbValue.endFbValue(builder);
    }

    private static int createArrayValue(FlatBufferBuilder builder, ArrayValue array) {
        int   size           = array.size();
        int[] elementOffsets = new int[size];
        int   i              = 0;
        for (Value element : array) {
            elementOffsets[i++] = createValue(builder, element);
        }
        int elementsVector = FbArrayValue.createElementsVector(builder, elementOffsets);
        return FbArrayValue.createFbArrayValue(builder, elementsVector);
    }

    private static int createObjectValue(FlatBufferBuilder builder, ObjectValue obj) {
        int   size         = obj.size();
        int[] fieldOffsets = new int[size];
        int   i            = 0;
        for (Map.Entry<String, Value> entry : obj.entrySet()) {
            int keyOffset   = builder.createString(entry.getKey());
            int valueOffset = createValue(builder, entry.getValue());
            fieldOffsets[i++] = FbObjectField.createFbObjectField(builder, keyOffset, valueOffset);
        }
        int fieldsVector = FbObjectValue.createFieldsVector(builder, fieldOffsets);
        return FbObjectValue.createFbObjectValue(builder, fieldsVector);
    }

    private static Value readValue(FbValue table) {
        byte kindType = table.kindType();
        if (kindType == VALUE_NONE) {
            return Value.UNDEFINED;
        }
        return switch (kindType) {
        case VALUE_NULL      -> Value.NULL;
        case VALUE_BOOL      -> {
            FbBoolValue boolVal = (FbBoolValue) table.kind(new FbBoolValue());
            yield Value.of(boolVal.value());
        }
        case VALUE_NUMBER    -> {
            FbNumberValue numVal = (FbNumberValue) table.kind(new FbNumberValue());
            yield new NumberValue(new BigDecimal(numVal.value()));
        }
        case VALUE_TEXT      -> {
            FbTextValue textVal = (FbTextValue) table.kind(new FbTextValue());
            yield Value.of(textVal.value());
        }
        case VALUE_ARRAY     -> {
            FbArrayValue arrVal = (FbArrayValue) table.kind(new FbArrayValue());
            yield readArrayValue(arrVal);
        }
        case VALUE_OBJECT    -> {
            FbObjectValue objVal = (FbObjectValue) table.kind(new FbObjectValue());
            yield readObjectValue(objVal);
        }
        case VALUE_UNDEFINED -> Value.UNDEFINED;
        case VALUE_ERROR     -> {
            FbErrorValue errVal = (FbErrorValue) table.kind(new FbErrorValue());
            yield Value.error(errVal.message());
        }
        default              -> Value.UNDEFINED;
        };
    }

    private static ArrayValue readArrayValue(FbArrayValue table) {
        ArrayValue.Builder builder = ArrayValue.builder();
        int                length  = table.elementsLength();
        for (int i = 0; i < length; i++) {
            builder.add(readValue(table.elements(i)));
        }
        return builder.build();
    }

    private static ObjectValue readObjectValue(FbObjectValue table) {
        ObjectValue.Builder builder = ObjectValue.builder();
        int                 length  = table.fieldsLength();
        for (int i = 0; i < length; i++) {
            FbObjectField field = table.fields(i);
            builder.put(field.key(), readValue(field.value()));
        }
        return builder.build();
    }

    /**
     * Serializes an AuthorizationSubscription to FlatBuffers bytes.
     *
     * @param subscription the subscription to serialize
     * @return FlatBuffers-encoded bytes
     */
    public static byte[] writeAuthorizationSubscription(AuthorizationSubscription subscription) {
        FlatBufferBuilder builder           = new FlatBufferBuilder(512);
        int               subjectOffset     = createValue(builder, subscription.subject());
        int               actionOffset      = createValue(builder, subscription.action());
        int               resourceOffset    = createValue(builder, subscription.resource());
        int               environmentOffset = createValue(builder, subscription.environment());
        int               secretsOffset     = createValue(builder, subscription.secrets());
        int               subOffset         = FbAuthorizationSubscription.createFbAuthorizationSubscription(builder,
                subjectOffset, actionOffset, resourceOffset, environmentOffset, secretsOffset);
        builder.finish(subOffset);
        return builder.sizedByteArray();
    }

    /**
     * Deserializes an AuthorizationSubscription from FlatBuffers bytes.
     *
     * @param bytes FlatBuffers-encoded bytes
     * @return the deserialized AuthorizationSubscription
     */
    public static AuthorizationSubscription readAuthorizationSubscription(byte[] bytes) {
        ByteBuffer                  buffer = ByteBuffer.wrap(bytes);
        FbAuthorizationSubscription table  = FbAuthorizationSubscription.getRootAsFbAuthorizationSubscription(buffer);
        return readAuthorizationSubscription(table);
    }

    private static AuthorizationSubscription readAuthorizationSubscription(FbAuthorizationSubscription table) {
        FbValue     subjectFb     = table.subject();
        FbValue     actionFb      = table.action();
        FbValue     resourceFb    = table.resource();
        FbValue     environmentFb = table.environment();
        FbValue     secretsFb     = table.secrets();
        Value       subject       = subjectFb != null ? readValue(subjectFb) : Value.UNDEFINED;
        Value       action        = actionFb != null ? readValue(actionFb) : Value.UNDEFINED;
        Value       resource      = resourceFb != null ? readValue(resourceFb) : Value.UNDEFINED;
        Value       environment   = environmentFb != null ? readValue(environmentFb) : Value.UNDEFINED;
        ObjectValue secrets       = secretsFb != null ? toObjectValue(readValue(secretsFb)) : Value.EMPTY_OBJECT;
        return new AuthorizationSubscription(subject, action, resource, environment, secrets);
    }

    private static ObjectValue toObjectValue(Value value) {
        if (value instanceof ObjectValue ov) {
            return ov;
        }
        return Value.EMPTY_OBJECT;
    }

    /**
     * Serializes an AuthorizationDecision to FlatBuffers bytes.
     *
     * @param decision the decision to serialize
     * @return FlatBuffers-encoded bytes
     */
    public static byte[] writeAuthorizationDecision(AuthorizationDecision decision) {
        FlatBufferBuilder builder           = new FlatBufferBuilder(512);
        int               obligationsOffset = createArrayValue(builder, decision.obligations());
        int               adviceOffset      = createArrayValue(builder, decision.advice());
        int               resourceOffset    = createValue(builder, decision.resource());
        int               decOffset         = FbAuthorizationDecision.createFbAuthorizationDecision(builder,
                toFlatBuffersDecision(decision.decision()), obligationsOffset, adviceOffset, resourceOffset);
        builder.finish(decOffset);
        return builder.sizedByteArray();
    }

    /**
     * Deserializes an AuthorizationDecision from FlatBuffers bytes.
     *
     * @param bytes FlatBuffers-encoded bytes
     * @return the deserialized AuthorizationDecision
     */
    public static AuthorizationDecision readAuthorizationDecision(byte[] bytes) {
        ByteBuffer              buffer = ByteBuffer.wrap(bytes);
        FbAuthorizationDecision table  = FbAuthorizationDecision.getRootAsFbAuthorizationDecision(buffer);
        return readAuthorizationDecision(table);
    }

    private static AuthorizationDecision readAuthorizationDecision(FbAuthorizationDecision table) {
        Decision     decision      = fromFlatBuffersDecision(table.decision());
        FbArrayValue obligationsFb = table.obligations();
        FbArrayValue adviceFb      = table.advice();
        FbValue      resourceFb    = table.resource();
        ArrayValue   obligations   = obligationsFb != null ? readArrayValue(obligationsFb) : Value.EMPTY_ARRAY;
        ArrayValue   advice        = adviceFb != null ? readArrayValue(adviceFb) : Value.EMPTY_ARRAY;
        Value        resource      = resourceFb != null ? readValue(resourceFb) : Value.UNDEFINED;
        return new AuthorizationDecision(decision, obligations, advice, resource);
    }

    private static byte toFlatBuffersDecision(Decision decision) {
        return switch (decision) {
        case PERMIT         -> DECISION_PERMIT;
        case DENY           -> DECISION_DENY;
        case NOT_APPLICABLE -> DECISION_NOT_APPLICABLE;
        case INDETERMINATE  -> DECISION_INDETERMINATE;
        };
    }

    private static Decision fromFlatBuffersDecision(byte value) {
        return switch (value) {
        case DECISION_PERMIT         -> Decision.PERMIT;
        case DECISION_DENY           -> Decision.DENY;
        case DECISION_NOT_APPLICABLE -> Decision.NOT_APPLICABLE;
        default                      -> Decision.INDETERMINATE;
        };
    }

    /**
     * Serializes a MultiAuthorizationSubscription to FlatBuffers bytes.
     *
     * @param multi the multi-subscription to serialize
     * @return FlatBuffers-encoded bytes
     */
    public static byte[] writeMultiAuthorizationSubscription(MultiAuthorizationSubscription multi) {
        FlatBufferBuilder builder    = new FlatBufferBuilder(1024);
        List<Integer>     subOffsets = new ArrayList<>();
        for (IdentifiableAuthorizationSubscription idSub : multi) {
            int idOffset  = builder.createString(idSub.subscriptionId());
            int subOffset = createAuthorizationSubscription(builder, idSub.subscription());
            subOffsets.add(FbIdentifiableAuthorizationSubscription
                    .createFbIdentifiableAuthorizationSubscription(builder, idOffset, subOffset));
        }
        int subsVector  = FbMultiAuthorizationSubscription.createSubscriptionsVector(builder, toIntArray(subOffsets));
        int multiOffset = FbMultiAuthorizationSubscription.createFbMultiAuthorizationSubscription(builder, subsVector);
        builder.finish(multiOffset);
        return builder.sizedByteArray();
    }

    private static int createAuthorizationSubscription(FlatBufferBuilder builder, AuthorizationSubscription sub) {
        int subjectOffset     = createValue(builder, sub.subject());
        int actionOffset      = createValue(builder, sub.action());
        int resourceOffset    = createValue(builder, sub.resource());
        int environmentOffset = createValue(builder, sub.environment());
        int secretsOffset     = createValue(builder, sub.secrets());
        return FbAuthorizationSubscription.createFbAuthorizationSubscription(builder, subjectOffset, actionOffset,
                resourceOffset, environmentOffset, secretsOffset);
    }

    /**
     * Deserializes a MultiAuthorizationSubscription from FlatBuffers bytes.
     *
     * @param bytes FlatBuffers-encoded bytes
     * @return the deserialized MultiAuthorizationSubscription
     */
    public static MultiAuthorizationSubscription readMultiAuthorizationSubscription(byte[] bytes) {
        ByteBuffer                       buffer = ByteBuffer.wrap(bytes);
        FbMultiAuthorizationSubscription table  = FbMultiAuthorizationSubscription
                .getRootAsFbMultiAuthorizationSubscription(buffer);
        MultiAuthorizationSubscription   result = new MultiAuthorizationSubscription();
        int                              length = table.subscriptionsLength();
        for (int i = 0; i < length; i++) {
            FbIdentifiableAuthorizationSubscription idSub = table.subscriptions(i);
            AuthorizationSubscription               sub   = readAuthorizationSubscription(idSub.subscription());
            result.addSubscription(idSub.subscriptionId(), sub);
        }
        return result;
    }

    /**
     * Serializes a MultiAuthorizationDecision to FlatBuffers bytes.
     *
     * @param multi the multi-decision to serialize
     * @return FlatBuffers-encoded bytes
     */
    public static byte[] writeMultiAuthorizationDecision(MultiAuthorizationDecision multi) {
        FlatBufferBuilder builder    = new FlatBufferBuilder(1024);
        List<Integer>     decOffsets = new ArrayList<>();
        for (IdentifiableAuthorizationDecision idDec : multi) {
            int idOffset  = builder.createString(idDec.subscriptionId());
            int decOffset = createAuthorizationDecision(builder, idDec.decision());
            decOffsets.add(FbDecisionEntry.createFbDecisionEntry(builder, idOffset, decOffset));
        }
        int decsVector  = FbMultiAuthorizationDecision.createDecisionsVector(builder, toIntArray(decOffsets));
        int multiOffset = FbMultiAuthorizationDecision.createFbMultiAuthorizationDecision(builder, decsVector);
        builder.finish(multiOffset);
        return builder.sizedByteArray();
    }

    private static int createAuthorizationDecision(FlatBufferBuilder builder, AuthorizationDecision decision) {
        int obligationsOffset = createArrayValue(builder, decision.obligations());
        int adviceOffset      = createArrayValue(builder, decision.advice());
        int resourceOffset    = createValue(builder, decision.resource());
        return FbAuthorizationDecision.createFbAuthorizationDecision(builder,
                toFlatBuffersDecision(decision.decision()), obligationsOffset, adviceOffset, resourceOffset);
    }

    /**
     * Deserializes a MultiAuthorizationDecision from FlatBuffers bytes.
     *
     * @param bytes FlatBuffers-encoded bytes
     * @return the deserialized MultiAuthorizationDecision
     */
    public static MultiAuthorizationDecision readMultiAuthorizationDecision(byte[] bytes) {
        ByteBuffer                   buffer = ByteBuffer.wrap(bytes);
        FbMultiAuthorizationDecision table  = FbMultiAuthorizationDecision
                .getRootAsFbMultiAuthorizationDecision(buffer);
        MultiAuthorizationDecision   result = new MultiAuthorizationDecision();
        int                          length = table.decisionsLength();
        for (int i = 0; i < length; i++) {
            FbDecisionEntry       entry = table.decisions(i);
            AuthorizationDecision dec   = readAuthorizationDecision(entry.decision());
            result.setDecision(entry.subscriptionId(), dec);
        }
        return result;
    }

    /**
     * Serializes an IdentifiableAuthorizationDecision to FlatBuffers bytes.
     *
     * @param idDec the identifiable decision to serialize
     * @return FlatBuffers-encoded bytes
     */
    public static byte[] writeIdentifiableAuthorizationDecision(IdentifiableAuthorizationDecision idDec) {
        FlatBufferBuilder builder   = new FlatBufferBuilder(512);
        int               idOffset  = builder.createString(idDec.subscriptionId());
        int               decOffset = createAuthorizationDecision(builder, idDec.decision());
        int               offset    = FbIdentifiableAuthorizationDecision
                .createFbIdentifiableAuthorizationDecision(builder, idOffset, decOffset);
        builder.finish(offset);
        return builder.sizedByteArray();
    }

    /**
     * Deserializes an IdentifiableAuthorizationDecision from FlatBuffers bytes.
     *
     * @param bytes FlatBuffers-encoded bytes
     * @return the deserialized IdentifiableAuthorizationDecision
     */
    public static IdentifiableAuthorizationDecision readIdentifiableAuthorizationDecision(byte[] bytes) {
        ByteBuffer                          buffer = ByteBuffer.wrap(bytes);
        FbIdentifiableAuthorizationDecision table  = FbIdentifiableAuthorizationDecision
                .getRootAsFbIdentifiableAuthorizationDecision(buffer);
        AuthorizationDecision               dec    = readAuthorizationDecision(table.decision());
        return new IdentifiableAuthorizationDecision(table.subscriptionId(), dec);
    }

    private static int[] toIntArray(List<Integer> list) {
        int   size   = list.size();
        int[] result = new int[size];
        for (int i = 0; i < size; i++) {
            result[i] = list.get(i);
        }
        return result;
    }
}
