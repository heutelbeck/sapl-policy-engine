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
package io.sapl.extensions.mqtt.util;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.hivemq.client.mqtt.datatypes.MqttUtf8String;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * This utility class provides functions to evaluate the format and to convert
 * the payload of a mqtt message to the defined format.
 */
@UtilityClass
public class PayloadFormatUtility {

    private static final String ERROR_MQTT_MESSAGE_JSON_CONVERSION_FAILED = "The mqtt message couldn't be converted to json.";

    /**
     * Looks up the payload format indicator from the mqtt publish message. By
     * default, it will be 0.
     *
     * @param publishMessage the published mqtt message
     * @return returns the payload format indicator as an ordinal number
     */
    public static int getPayloadFormatIndicator(Mqtt5Publish publishMessage) {
        val payloadFormatIndicatorOptional = publishMessage.getPayloadFormatIndicator();
        var payloadFormatIndicator         = 0;
        if (payloadFormatIndicatorOptional.isPresent()) {
            // specified whether the payload is utf-8 encoded
            payloadFormatIndicator = payloadFormatIndicatorOptional.get().getCode();
        }
        return payloadFormatIndicator;
    }

    /**
     * Looks up the content type.
     *
     * @param publishMessage the published mqtt message
     * @return returns the content type or null in case no content type was
     * specified
     */
    public static String getContentType(Mqtt5Publish publishMessage) {
        Optional<MqttUtf8String> contentTypeOptional = publishMessage.getContentType(); // specifies kind of utf-8
                                                                                        // encoded payload
        return contentTypeOptional.map(Object::toString).orElse(null);
    }

    /**
     * Checks whether the given byte array can be converted in a UTF-8 string.
     *
     * @param bytes the byte array to be checked for conversion
     * @return returns true if a conversions into a UTF-8 string is possible,
     * otherwise false
     */
    public static boolean isValidUtf8String(byte[] bytes) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
        try {
            decoder.decode(ByteBuffer.wrap(bytes));
        } catch (CharacterCodingException ex) {
            return false;
        }
        return true;
    }

    /**
     * Converts the payload into a json object and builds a {@link Value} of it.
     *
     * @param publishMessage the published mqtt message
     * @return returns the build {@link Value} of the json object or an error Value
     * in case the mqtt message could not be converted into a json object.
     */
    public static Value getValueOfJson(Mqtt5Publish publishMessage) {
        try {
            return ValueJsonMarshaller.fromJsonNode(convertBytesToJson(publishMessage.getPayloadAsBytes()));
        } catch (JacksonException e) {
            return Value.error(ERROR_MQTT_MESSAGE_JSON_CONVERSION_FAILED);
        }
    }

    private static JsonNode convertBytesToJson(byte[] bytes) {
        return JsonMapper.builder().build().readTree(bytes);
    }

    /**
     * Converts the given array of bytes to an ArrayValue containing the bytes as
     * integers.
     *
     * @param bytes the given array of bytes to convert
     * @return an ArrayValue containing the bytes as integer values
     */
    public static Value convertBytesToArrayValue(byte[] bytes) {
        var values = new Value[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            values[i] = Value.of(bytes[i]);
        }
        return Value.ofArray(values);
    }

}
