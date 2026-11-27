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
package io.sapl.pdp.remote;

import java.io.IOException;

import io.sapl.api.binary.SaplBinaryCodec;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.api.proto.SaplProtobufCodec;

/**
 * The encoding of the payloads of an RSocket connection to the node, which the
 * SETUP frame names as the data MIME type.
 */
enum PayloadFormat {

    /** {@code sapl_types.proto}, under the data MIME type of rsocket-java. */
    PROTOBUF,
    /** The SAPL binary format, under its own data MIME type. */
    BINARY;

    /** The data MIME type of rsocket-java when none is configured, which the node reads as protobuf. */
    private static final String PROTOBUF_MIME_TYPE = "application/binary";

    String mimeType() {
        return switch (this) {
        case PROTOBUF -> PROTOBUF_MIME_TYPE;
        case BINARY   -> SaplBinaryCodec.MIME_TYPE;
        };
    }

    byte[] write(AuthorizationSubscription subscription) throws IOException {
        return switch (this) {
        case PROTOBUF -> SaplProtobufCodec.writeAuthorizationSubscription(subscription);
        case BINARY   -> SaplBinaryCodec.writeAuthorizationSubscription(subscription);
        };
    }

    byte[] write(MultiAuthorizationSubscription subscriptions) throws IOException {
        return switch (this) {
        case PROTOBUF -> SaplProtobufCodec.writeMultiAuthorizationSubscription(subscriptions);
        case BINARY   -> SaplBinaryCodec.writeMultiAuthorizationSubscription(subscriptions);
        };
    }

    AuthorizationDecision readDecision(byte[] answer) throws IOException {
        return switch (this) {
        case PROTOBUF -> SaplProtobufCodec.readAuthorizationDecision(answer);
        case BINARY   -> SaplBinaryCodec.readAuthorizationDecision(answer);
        };
    }

    IdentifiableAuthorizationDecision readIdentifiableDecision(byte[] answer) throws IOException {
        return switch (this) {
        case PROTOBUF -> SaplProtobufCodec.readIdentifiableAuthorizationDecision(answer);
        case BINARY   -> SaplBinaryCodec.readIdentifiableAuthorizationDecision(answer);
        };
    }

    MultiAuthorizationDecision readMultiDecision(byte[] answer) throws IOException {
        return switch (this) {
        case PROTOBUF -> SaplProtobufCodec.readMultiAuthorizationDecision(answer);
        case BINARY   -> SaplBinaryCodec.readMultiAuthorizationDecision(answer);
        };
    }
}
