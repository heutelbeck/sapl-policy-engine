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
package io.sapl.api.proto;

import java.io.IOException;
import java.math.BigDecimal;

import static com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED;
import static com.google.protobuf.WireFormat.getTagFieldNumber;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;

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
import lombok.val;

/**
 * Direct protobuf wire format codec for SAPL domain types.
 * <p>
 * Serializes/deserializes directly to/from domain objects without intermediate
 * generated protobuf classes. Wire format is compatible with the .proto schema
 * definitions in src/main/proto/.
 * <p>
 * Field numbers match sapl_types.proto exactly.
 */
@UtilityClass
public class SaplProtobufCodec {

    // Value oneof field numbers (from sapl_types.proto)
    private static final int VALUE_NULL      = 1;
    private static final int VALUE_BOOL      = 2;
    private static final int VALUE_NUMBER    = 3;
    private static final int VALUE_TEXT      = 4;
    private static final int VALUE_ARRAY     = 5;
    private static final int VALUE_OBJECT    = 6;
    private static final int VALUE_UNDEFINED = 7;
    private static final int VALUE_ERROR     = 8;

    // ArrayValue field numbers
    private static final int ARRAY_ELEMENTS = 1;

    // ObjectValue field numbers (map<string, Value>)
    private static final int OBJECT_FIELDS   = 1;
    private static final int MAP_ENTRY_KEY   = 1;
    private static final int MAP_ENTRY_VALUE = 2;

    // ErrorValue field numbers
    private static final int ERROR_MESSAGE   = 1;
    private static final int ERROR_ARGUMENTS = 2;

    // AuthorizationSubscription field numbers
    private static final int SUBSCRIPTION_SUBJECT     = 1;
    private static final int SUBSCRIPTION_ACTION      = 2;
    private static final int SUBSCRIPTION_RESOURCE    = 3;
    private static final int SUBSCRIPTION_ENVIRONMENT = 4;
    private static final int SUBSCRIPTION_SECRETS     = 5;

    // AuthorizationDecision field numbers
    private static final int DECISION_DECISION    = 1;
    private static final int DECISION_OBLIGATIONS = 2;
    private static final int DECISION_ADVICE      = 3;
    private static final int DECISION_RESOURCE    = 4;

    // Decision enum values (INDETERMINATE=0 is proto default)
    private static final int DECISION_ENUM_INDETERMINATE  = 0;
    private static final int DECISION_ENUM_PERMIT         = 1;
    private static final int DECISION_ENUM_DENY           = 2;
    private static final int DECISION_ENUM_NOT_APPLICABLE = 3;

    // IdentifiableAuthorizationSubscription field numbers
    private static final int ID_SUB_ID           = 1;
    private static final int ID_SUB_SUBSCRIPTION = 2;

    // IdentifiableAuthorizationDecision field numbers
    private static final int ID_DEC_ID       = 1;
    private static final int ID_DEC_DECISION = 2;

    // MultiAuthorizationSubscription field numbers
    private static final int MULTI_SUB_SUBSCRIPTIONS = 1;

    // MultiAuthorizationDecision field numbers
    private static final int MULTI_DEC_DECISIONS = 1;

    /**
     * Serializes a Value to protobuf bytes.
     *
     * @param value the value to serialize
     * @return protobuf-encoded bytes
     * @throws IOException if serialization fails
     */
    public static byte[] writeValue(Value value) throws IOException {
        val size   = computeValueSize(value);
        val buffer = new byte[size];
        val output = CodedOutputStream.newInstance(buffer);
        writeValueFields(value, output);
        output.checkNoSpaceLeft();
        return buffer;
    }

    /**
     * Deserializes a Value from protobuf bytes.
     *
     * @param bytes protobuf-encoded bytes
     * @return the deserialized Value
     * @throws IOException if deserialization fails
     */
    public static Value readValue(byte[] bytes) throws IOException {
        return readValueFields(CodedInputStream.newInstance(bytes));
    }

    private static Value readEmbeddedValue(CodedInputStream input) throws IOException {
        val limit = input.pushLimit(input.readRawVarint32());
        val value = readValueFields(input);
        input.popLimit(limit);
        return value;
    }

    private static Value readValueFields(CodedInputStream input) throws IOException {
        Value result = Value.UNDEFINED;
        while (!input.isAtEnd()) {
            val tag         = input.readTag();
            val fieldNumber = getTagFieldNumber(tag);
            result = switch (fieldNumber) {
            case VALUE_NULL      -> {
                input.skipRawBytes(input.readRawVarint32());
                yield Value.NULL;
            }
            case VALUE_BOOL      -> Value.of(input.readBool());
            case VALUE_NUMBER    -> new NumberValue(new BigDecimal(input.readString()));
            case VALUE_TEXT      -> Value.of(input.readString());
            case VALUE_ARRAY     -> readArrayValue(input);
            case VALUE_OBJECT    -> readObjectValue(input);
            case VALUE_UNDEFINED -> {
                input.readBool();
                yield Value.UNDEFINED;
            }
            case VALUE_ERROR     -> readErrorValue(input);
            default              -> {
                input.skipField(tag);
                yield result;
            }
            };
        }
        return result;
    }

    private static ArrayValue readArrayValue(CodedInputStream input) throws IOException {
        val limit   = input.pushLimit(input.readRawVarint32());
        val builder = ArrayValue.builder();
        while (!input.isAtEnd()) {
            val tag = input.readTag();
            if (getTagFieldNumber(tag) == ARRAY_ELEMENTS) {
                builder.add(readEmbeddedValue(input));
            } else {
                input.skipField(tag);
            }
        }
        input.popLimit(limit);
        return builder.build();
    }

    private static ObjectValue readObjectValue(CodedInputStream input) throws IOException {
        val limit   = input.pushLimit(input.readRawVarint32());
        val builder = ObjectValue.builder();
        while (!input.isAtEnd()) {
            val tag = input.readTag();
            if (getTagFieldNumber(tag) == OBJECT_FIELDS) {
                readMapEntry(input, builder);
            } else {
                input.skipField(tag);
            }
        }
        input.popLimit(limit);
        return builder.build();
    }

    private static void readMapEntry(CodedInputStream input, ObjectValue.Builder builder) throws IOException {
        val    limit = input.pushLimit(input.readRawVarint32());
        String key   = "";
        Value  value = Value.UNDEFINED;
        while (!input.isAtEnd()) {
            val tag         = input.readTag();
            val fieldNumber = getTagFieldNumber(tag);
            switch (fieldNumber) {
            case MAP_ENTRY_KEY   -> key = input.readString();
            case MAP_ENTRY_VALUE -> value = readEmbeddedValue(input);
            default              -> input.skipField(tag);
            }
        }
        input.popLimit(limit);
        builder.put(key, value);
    }

    private static ErrorValue readErrorValue(CodedInputStream input) throws IOException {
        val limit   = input.pushLimit(input.readRawVarint32());
        var message = "";
        while (!input.isAtEnd()) {
            val tag         = input.readTag();
            val fieldNumber = getTagFieldNumber(tag);
            switch (fieldNumber) {
            case ERROR_MESSAGE   -> message = input.readString();
            case ERROR_ARGUMENTS -> input.readString(); // ErrorValue only uses message
            default              -> input.skipField(tag);
            }
        }
        input.popLimit(limit);
        return Value.error(message);
    }

    private static void writeValueFields(Value value, CodedOutputStream output) throws IOException {
        switch (value) {
        case NullValue ignored         -> {
            output.writeTag(VALUE_NULL, WIRETYPE_LENGTH_DELIMITED);
            output.writeUInt32NoTag(0); // empty message
        }
        case BooleanValue(boolean b)   -> output.writeBool(VALUE_BOOL, b);
        case NumberValue(BigDecimal n) -> output.writeString(VALUE_NUMBER, n.toPlainString());
        case TextValue(String s)       -> output.writeString(VALUE_TEXT, s);
        case ArrayValue arr            -> writeArrayValue(arr, output);
        case ObjectValue obj           -> writeObjectValue(obj, output);
        case UndefinedValue ignored    -> output.writeBool(VALUE_UNDEFINED, true);
        case ErrorValue err            -> writeErrorValue(err, output);
        }
    }

    private static void writeArrayValue(ArrayValue array, CodedOutputStream output) throws IOException {
        writeArrayValueField(VALUE_ARRAY, array, output);
    }

    private static void writeObjectValue(ObjectValue obj, CodedOutputStream output) throws IOException {
        output.writeTag(VALUE_OBJECT, WIRETYPE_LENGTH_DELIMITED);
        output.writeUInt32NoTag(computeObjectValueContentSize(obj));
        for (val entry : obj.entrySet()) {
            writeMapEntry(entry.getKey(), entry.getValue(), output);
        }
    }

    private static void writeMapEntry(String key, Value value, CodedOutputStream output) throws IOException {
        output.writeTag(OBJECT_FIELDS, WIRETYPE_LENGTH_DELIMITED);
        val entrySize = CodedOutputStream.computeStringSizeNoTag(key) + 1 // key field tag
                + CodedOutputStream.computeUInt32SizeNoTag(computeValueSize(value)) + 1 // value field tag + length
                + computeValueSize(value);
        output.writeUInt32NoTag(entrySize);
        output.writeString(MAP_ENTRY_KEY, key);
        output.writeTag(MAP_ENTRY_VALUE, WIRETYPE_LENGTH_DELIMITED);
        output.writeUInt32NoTag(computeValueSize(value));
        writeValueFields(value, output);
    }

    private static void writeErrorValue(ErrorValue error, CodedOutputStream output) throws IOException {
        output.writeTag(VALUE_ERROR, WIRETYPE_LENGTH_DELIMITED);
        val contentSize = CodedOutputStream.computeStringSize(ERROR_MESSAGE, error.message());
        output.writeUInt32NoTag(contentSize);
        output.writeString(ERROR_MESSAGE, error.message());
    }

    private static int computeValueSize(Value value) {
        return switch (value) {
        case NullValue ignored         -> 1 + 1; // tag + 0 length
        case BooleanValue ignored      -> 1 + 1; // tag + bool
        case NumberValue(BigDecimal n) -> CodedOutputStream.computeStringSize(VALUE_NUMBER, n.toPlainString());
        case TextValue(String s)       -> CodedOutputStream.computeStringSize(VALUE_TEXT, s);
        case ArrayValue arr            ->
            1 + CodedOutputStream.computeUInt32SizeNoTag(computeArrayValueContentSize(arr))
                    + computeArrayValueContentSize(arr);
        case ObjectValue obj           ->
            1 + CodedOutputStream.computeUInt32SizeNoTag(computeObjectValueContentSize(obj))
                    + computeObjectValueContentSize(obj);
        case UndefinedValue ignored    -> 1 + 1; // tag + bool
        case ErrorValue err            -> 1
                + CodedOutputStream
                        .computeUInt32SizeNoTag(CodedOutputStream.computeStringSize(ERROR_MESSAGE, err.message()))
                + CodedOutputStream.computeStringSize(ERROR_MESSAGE, err.message());
        };
    }

    private static int computeArrayValueContentSize(ArrayValue array) {
        var size = 0;
        for (val element : array) {
            val elementSize = computeValueSize(element);
            size += 1 + CodedOutputStream.computeUInt32SizeNoTag(elementSize) + elementSize;
        }
        return size;
    }

    private static int computeObjectValueContentSize(ObjectValue obj) {
        var size = 0;
        for (val entry : obj.entrySet()) {
            val valueSize = computeValueSize(entry.getValue());
            val entrySize = CodedOutputStream.computeStringSizeNoTag(entry.getKey()) + 1
                    + CodedOutputStream.computeUInt32SizeNoTag(valueSize) + 1 + valueSize;
            size += 1 + CodedOutputStream.computeUInt32SizeNoTag(entrySize) + entrySize;
        }
        return size;
    }

    /**
     * Serializes an AuthorizationSubscription to protobuf bytes.
     *
     * @param subscription the subscription to serialize
     * @return protobuf-encoded bytes
     * @throws IOException if serialization fails
     */
    public static byte[] writeAuthorizationSubscription(AuthorizationSubscription subscription) throws IOException {
        val size   = computeAuthorizationSubscriptionSize(subscription);
        val buffer = new byte[size];
        val output = CodedOutputStream.newInstance(buffer);
        writeAuthorizationSubscriptionFields(subscription, output);
        output.checkNoSpaceLeft();
        return buffer;
    }

    /**
     * Deserializes an AuthorizationSubscription from protobuf bytes.
     *
     * @param bytes protobuf-encoded bytes
     * @return the deserialized AuthorizationSubscription
     * @throws IOException if deserialization fails
     */
    public static AuthorizationSubscription readAuthorizationSubscription(byte[] bytes) throws IOException {
        return readAuthorizationSubscription(CodedInputStream.newInstance(bytes));
    }

    private static AuthorizationSubscription readAuthorizationSubscription(CodedInputStream input) throws IOException {
        Value       subject     = Value.UNDEFINED;
        Value       action      = Value.UNDEFINED;
        Value       resource    = Value.UNDEFINED;
        Value       environment = Value.UNDEFINED;
        ObjectValue secrets     = Value.EMPTY_OBJECT;

        while (!input.isAtEnd()) {
            val tag         = input.readTag();
            val fieldNumber = getTagFieldNumber(tag);
            switch (fieldNumber) {
            case SUBSCRIPTION_SUBJECT     -> subject = readEmbeddedValue(input);
            case SUBSCRIPTION_ACTION      -> action = readEmbeddedValue(input);
            case SUBSCRIPTION_RESOURCE    -> resource = readEmbeddedValue(input);
            case SUBSCRIPTION_ENVIRONMENT -> environment = readEmbeddedValue(input);
            case SUBSCRIPTION_SECRETS     -> secrets = toObjectValue(readEmbeddedValue(input));
            default                       -> input.skipField(tag);
            }
        }
        return new AuthorizationSubscription(subject, action, resource, environment, secrets);
    }

    private static ObjectValue toObjectValue(Value value) {
        if (value instanceof ObjectValue ov) {
            return ov;
        }
        return Value.EMPTY_OBJECT;
    }

    private static void writeAuthorizationSubscriptionFields(AuthorizationSubscription sub, CodedOutputStream output)
            throws IOException {
        writeValueField(SUBSCRIPTION_SUBJECT, sub.subject(), output);
        writeValueField(SUBSCRIPTION_ACTION, sub.action(), output);
        writeValueField(SUBSCRIPTION_RESOURCE, sub.resource(), output);
        writeValueField(SUBSCRIPTION_ENVIRONMENT, sub.environment(), output);
        writeValueField(SUBSCRIPTION_SECRETS, sub.secrets(), output);
    }

    private static void writeValueField(int fieldNumber, Value value, CodedOutputStream output) throws IOException {
        output.writeTag(fieldNumber, WIRETYPE_LENGTH_DELIMITED);
        output.writeUInt32NoTag(computeValueSize(value));
        writeValueFields(value, output);
    }

    private static int computeAuthorizationSubscriptionSize(AuthorizationSubscription sub) {
        return computeValueFieldSize(SUBSCRIPTION_SUBJECT, sub.subject())
                + computeValueFieldSize(SUBSCRIPTION_ACTION, sub.action())
                + computeValueFieldSize(SUBSCRIPTION_RESOURCE, sub.resource())
                + computeValueFieldSize(SUBSCRIPTION_ENVIRONMENT, sub.environment())
                + computeValueFieldSize(SUBSCRIPTION_SECRETS, sub.secrets());
    }

    private static int computeValueFieldSize(int fieldNumber, Value value) {
        val contentSize = computeValueSize(value);
        return CodedOutputStream.computeTagSize(fieldNumber) + CodedOutputStream.computeUInt32SizeNoTag(contentSize)
                + contentSize;
    }

    /**
     * Serializes an AuthorizationDecision to protobuf bytes.
     *
     * @param decision the decision to serialize
     * @return protobuf-encoded bytes
     * @throws IOException if serialization fails
     */
    public static byte[] writeAuthorizationDecision(AuthorizationDecision decision) throws IOException {
        val size   = computeAuthorizationDecisionSize(decision);
        val buffer = new byte[size];
        val output = CodedOutputStream.newInstance(buffer);
        writeAuthorizationDecisionFields(decision, output);
        output.checkNoSpaceLeft();
        return buffer;
    }

    /**
     * Deserializes an AuthorizationDecision from protobuf bytes.
     *
     * @param bytes protobuf-encoded bytes
     * @return the deserialized AuthorizationDecision
     * @throws IOException if deserialization fails
     */
    public static AuthorizationDecision readAuthorizationDecision(byte[] bytes) throws IOException {
        return readAuthorizationDecision(CodedInputStream.newInstance(bytes));
    }

    private static AuthorizationDecision readAuthorizationDecision(CodedInputStream input) throws IOException {
        Decision   decision    = Decision.INDETERMINATE;
        ArrayValue obligations = Value.EMPTY_ARRAY;
        ArrayValue advice      = Value.EMPTY_ARRAY;
        Value      resource    = Value.UNDEFINED;

        while (!input.isAtEnd()) {
            val tag         = input.readTag();
            val fieldNumber = getTagFieldNumber(tag);
            switch (fieldNumber) {
            case DECISION_DECISION    -> decision = readDecision(input);
            case DECISION_OBLIGATIONS -> obligations = readArrayValue(input);
            case DECISION_ADVICE      -> advice = readArrayValue(input);
            case DECISION_RESOURCE    -> resource = readEmbeddedValue(input);
            default                   -> input.skipField(tag);
            }
        }
        return new AuthorizationDecision(decision, obligations, advice, resource);
    }

    private static Decision readDecision(CodedInputStream input) throws IOException {
        return switch (input.readEnum()) {
        case DECISION_ENUM_PERMIT         -> Decision.PERMIT;
        case DECISION_ENUM_DENY           -> Decision.DENY;
        case DECISION_ENUM_NOT_APPLICABLE -> Decision.NOT_APPLICABLE;
        default                           -> Decision.INDETERMINATE;
        };
    }

    private static void writeAuthorizationDecisionFields(AuthorizationDecision dec, CodedOutputStream output)
            throws IOException {
        output.writeEnum(DECISION_DECISION, toProtoDecision(dec.decision()));
        writeArrayValueField(DECISION_OBLIGATIONS, dec.obligations(), output);
        writeArrayValueField(DECISION_ADVICE, dec.advice(), output);
        writeValueField(DECISION_RESOURCE, dec.resource(), output);
    }

    private static void writeArrayValueField(int fieldNumber, ArrayValue array, CodedOutputStream output)
            throws IOException {
        output.writeTag(fieldNumber, WIRETYPE_LENGTH_DELIMITED);
        output.writeUInt32NoTag(computeArrayValueContentSize(array));
        for (val element : array) {
            output.writeTag(ARRAY_ELEMENTS, WIRETYPE_LENGTH_DELIMITED);
            output.writeUInt32NoTag(computeValueSize(element));
            writeValueFields(element, output);
        }
    }

    private static int toProtoDecision(Decision decision) {
        return switch (decision) {
        case PERMIT         -> DECISION_ENUM_PERMIT;
        case DENY           -> DECISION_ENUM_DENY;
        case NOT_APPLICABLE -> DECISION_ENUM_NOT_APPLICABLE;
        case INDETERMINATE  -> DECISION_ENUM_INDETERMINATE;
        };
    }

    private static int computeAuthorizationDecisionSize(AuthorizationDecision dec) {
        return CodedOutputStream.computeEnumSize(DECISION_DECISION, toProtoDecision(dec.decision()))
                + computeArrayValueFieldSize(DECISION_OBLIGATIONS, dec.obligations())
                + computeArrayValueFieldSize(DECISION_ADVICE, dec.advice())
                + computeValueFieldSize(DECISION_RESOURCE, dec.resource());
    }

    private static int computeArrayValueFieldSize(int fieldNumber, ArrayValue array) {
        val contentSize = computeArrayValueContentSize(array);
        return CodedOutputStream.computeTagSize(fieldNumber) + CodedOutputStream.computeUInt32SizeNoTag(contentSize)
                + contentSize;
    }

    /**
     * Serializes a MultiAuthorizationSubscription to protobuf bytes.
     *
     * @param multi the multi-subscription to serialize
     * @return protobuf-encoded bytes
     * @throws IOException if serialization fails
     */
    public static byte[] writeMultiAuthorizationSubscription(MultiAuthorizationSubscription multi) throws IOException {
        val size   = computeMultiAuthorizationSubscriptionSize(multi);
        val buffer = new byte[size];
        val output = CodedOutputStream.newInstance(buffer);
        for (val idSub : multi) {
            writeIdentifiableSubscription(idSub, output);
        }
        output.checkNoSpaceLeft();
        return buffer;
    }

    /**
     * Deserializes a MultiAuthorizationSubscription from protobuf bytes.
     *
     * @param bytes protobuf-encoded bytes
     * @return the deserialized MultiAuthorizationSubscription
     * @throws IOException if deserialization fails
     */
    public static MultiAuthorizationSubscription readMultiAuthorizationSubscription(byte[] bytes) throws IOException {
        val input  = CodedInputStream.newInstance(bytes);
        val result = new MultiAuthorizationSubscription();
        while (!input.isAtEnd()) {
            val tag = input.readTag();
            if (getTagFieldNumber(tag) == MULTI_SUB_SUBSCRIPTIONS) {
                val idSub = readIdentifiableSubscription(input);
                result.addSubscription(idSub.subscriptionId(), idSub.subscription());
            } else {
                input.skipField(tag);
            }
        }
        return result;
    }

    private static final AuthorizationSubscription EMPTY_SUBSCRIPTION = new AuthorizationSubscription(Value.UNDEFINED,
            Value.UNDEFINED, Value.UNDEFINED, Value.UNDEFINED, Value.EMPTY_OBJECT);

    private static IdentifiableAuthorizationSubscription readIdentifiableSubscription(CodedInputStream input)
            throws IOException {
        val                       limit = input.pushLimit(input.readRawVarint32());
        String                    id    = "";
        AuthorizationSubscription sub   = EMPTY_SUBSCRIPTION;
        while (!input.isAtEnd()) {
            val tag         = input.readTag();
            val fieldNumber = getTagFieldNumber(tag);
            switch (fieldNumber) {
            case ID_SUB_ID           -> id = input.readString();
            case ID_SUB_SUBSCRIPTION -> sub = readEmbeddedSubscription(input);
            default                  -> input.skipField(tag);
            }
        }
        input.popLimit(limit);
        return new IdentifiableAuthorizationSubscription(id, sub);
    }

    private static AuthorizationSubscription readEmbeddedSubscription(CodedInputStream input) throws IOException {
        val limit  = input.pushLimit(input.readRawVarint32());
        val result = readAuthorizationSubscription(input);
        input.popLimit(limit);
        return result;
    }

    private static void writeIdentifiableSubscription(IdentifiableAuthorizationSubscription idSub,
            CodedOutputStream output) throws IOException {
        output.writeTag(MULTI_SUB_SUBSCRIPTIONS, WIRETYPE_LENGTH_DELIMITED);
        val contentSize = CodedOutputStream.computeStringSize(ID_SUB_ID, idSub.subscriptionId())
                + computeEmbeddedSubscriptionSize(idSub.subscription());
        output.writeUInt32NoTag(contentSize);
        output.writeString(ID_SUB_ID, idSub.subscriptionId());
        output.writeTag(ID_SUB_SUBSCRIPTION, WIRETYPE_LENGTH_DELIMITED);
        val subSize = computeAuthorizationSubscriptionSize(idSub.subscription());
        output.writeUInt32NoTag(subSize);
        writeAuthorizationSubscriptionFields(idSub.subscription(), output);
    }

    private static int computeEmbeddedSubscriptionSize(AuthorizationSubscription sub) {
        val subSize = computeAuthorizationSubscriptionSize(sub);
        return CodedOutputStream.computeTagSize(ID_SUB_SUBSCRIPTION) + CodedOutputStream.computeUInt32SizeNoTag(subSize)
                + subSize;
    }

    private static int computeMultiAuthorizationSubscriptionSize(MultiAuthorizationSubscription multi) {
        var size = 0;
        for (val idSub : multi) {
            val contentSize = CodedOutputStream.computeStringSize(ID_SUB_ID, idSub.subscriptionId())
                    + computeEmbeddedSubscriptionSize(idSub.subscription());
            size += CodedOutputStream.computeTagSize(MULTI_SUB_SUBSCRIPTIONS)
                    + CodedOutputStream.computeUInt32SizeNoTag(contentSize) + contentSize;
        }
        return size;
    }

    /**
     * Serializes a MultiAuthorizationDecision to protobuf bytes.
     *
     * @param multi the multi-decision to serialize
     * @return protobuf-encoded bytes
     * @throws IOException if serialization fails
     */
    public static byte[] writeMultiAuthorizationDecision(MultiAuthorizationDecision multi) throws IOException {
        val size   = computeMultiAuthorizationDecisionSize(multi);
        val buffer = new byte[size];
        val output = CodedOutputStream.newInstance(buffer);
        for (val idDec : multi) {
            writeDecisionMapEntry(idDec.subscriptionId(), idDec.decision(), output);
        }
        output.checkNoSpaceLeft();
        return buffer;
    }

    /**
     * Deserializes a MultiAuthorizationDecision from protobuf bytes.
     *
     * @param bytes protobuf-encoded bytes
     * @return the deserialized MultiAuthorizationDecision
     * @throws IOException if deserialization fails
     */
    public static MultiAuthorizationDecision readMultiAuthorizationDecision(byte[] bytes) throws IOException {
        val input  = CodedInputStream.newInstance(bytes);
        val result = new MultiAuthorizationDecision();
        while (!input.isAtEnd()) {
            val tag = input.readTag();
            if (getTagFieldNumber(tag) == MULTI_DEC_DECISIONS) {
                readDecisionMapEntry(input, result);
            } else {
                input.skipField(tag);
            }
        }
        return result;
    }

    private static void readDecisionMapEntry(CodedInputStream input, MultiAuthorizationDecision result)
            throws IOException {
        val limit = input.pushLimit(input.readRawVarint32());
        val idDec = readIdAndDecisionFields(input);
        input.popLimit(limit);
        result.setDecision(idDec.subscriptionId(), idDec.decision());
    }

    private static IdentifiableAuthorizationDecision readIdAndDecisionFields(CodedInputStream input)
            throws IOException {
        String                id  = "";
        AuthorizationDecision dec = AuthorizationDecision.INDETERMINATE;
        while (!input.isAtEnd()) {
            val tag         = input.readTag();
            val fieldNumber = getTagFieldNumber(tag);
            switch (fieldNumber) {
            case ID_DEC_ID       -> id = input.readString();
            case ID_DEC_DECISION -> dec = readEmbeddedDecision(input);
            default              -> input.skipField(tag);
            }
        }
        return new IdentifiableAuthorizationDecision(id, dec);
    }

    private static AuthorizationDecision readEmbeddedDecision(CodedInputStream input) throws IOException {
        val limit  = input.pushLimit(input.readRawVarint32());
        val result = readAuthorizationDecision(input);
        input.popLimit(limit);
        return result;
    }

    private static void writeDecisionMapEntry(String id, AuthorizationDecision dec, CodedOutputStream output)
            throws IOException {
        output.writeTag(MULTI_DEC_DECISIONS, WIRETYPE_LENGTH_DELIMITED);
        val decSize     = computeAuthorizationDecisionSize(dec);
        val contentSize = CodedOutputStream.computeStringSizeNoTag(id) + 1
                + CodedOutputStream.computeUInt32SizeNoTag(decSize) + 1 + decSize;
        output.writeUInt32NoTag(contentSize);
        output.writeString(MAP_ENTRY_KEY, id);
        output.writeTag(MAP_ENTRY_VALUE, WIRETYPE_LENGTH_DELIMITED);
        output.writeUInt32NoTag(decSize);
        writeAuthorizationDecisionFields(dec, output);
    }

    private static int computeMultiAuthorizationDecisionSize(MultiAuthorizationDecision multi) {
        var size = 0;
        for (val idDec : multi) {
            val decSize     = computeAuthorizationDecisionSize(idDec.decision());
            val contentSize = CodedOutputStream.computeStringSizeNoTag(idDec.subscriptionId()) + 1
                    + CodedOutputStream.computeUInt32SizeNoTag(decSize) + 1 + decSize;
            size += CodedOutputStream.computeTagSize(MULTI_DEC_DECISIONS)
                    + CodedOutputStream.computeUInt32SizeNoTag(contentSize) + contentSize;
        }
        return size;
    }

    /**
     * Serializes an IdentifiableAuthorizationDecision to protobuf bytes.
     *
     * @param idDec the identifiable decision to serialize
     * @return protobuf-encoded bytes
     * @throws IOException if serialization fails
     */
    public static byte[] writeIdentifiableAuthorizationDecision(IdentifiableAuthorizationDecision idDec)
            throws IOException {
        val size   = computeIdentifiableAuthorizationDecisionSize(idDec);
        val buffer = new byte[size];
        val output = CodedOutputStream.newInstance(buffer);
        output.writeString(ID_DEC_ID, idDec.subscriptionId());
        output.writeTag(ID_DEC_DECISION, WIRETYPE_LENGTH_DELIMITED);
        val decSize = computeAuthorizationDecisionSize(idDec.decision());
        output.writeUInt32NoTag(decSize);
        writeAuthorizationDecisionFields(idDec.decision(), output);
        output.checkNoSpaceLeft();
        return buffer;
    }

    /**
     * Deserializes an IdentifiableAuthorizationDecision from protobuf bytes.
     *
     * @param bytes protobuf-encoded bytes
     * @return the deserialized IdentifiableAuthorizationDecision
     * @throws IOException if deserialization fails
     */
    public static IdentifiableAuthorizationDecision readIdentifiableAuthorizationDecision(byte[] bytes)
            throws IOException {
        return readIdAndDecisionFields(CodedInputStream.newInstance(bytes));
    }

    private static int computeIdentifiableAuthorizationDecisionSize(IdentifiableAuthorizationDecision idDec) {
        val decSize = computeAuthorizationDecisionSize(idDec.decision());
        return CodedOutputStream.computeStringSize(ID_DEC_ID, idDec.subscriptionId())
                + CodedOutputStream.computeTagSize(ID_DEC_DECISION) + CodedOutputStream.computeUInt32SizeNoTag(decSize)
                + decSize;
    }
}
