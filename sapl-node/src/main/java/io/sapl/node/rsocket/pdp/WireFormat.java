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
package io.sapl.node.rsocket.pdp;

import java.io.IOException;

import org.jspecify.annotations.Nullable;

import io.sapl.api.binary.SaplBinaryCodec;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.api.proto.SaplProtobufCodec;

/**
 * The encoding of the payloads of an RSocket connection, which the data MIME
 * type of its SETUP frame names: protobuf ({@code sapl_types.proto}) under
 * {@code application/binary}, the default of rsocket-java, and under its alias
 * {@code application/x-protobuf}, or the SAPL binary format under
 * {@link SaplBinaryCodec#MIME_TYPE}. The MIME type is compared byte for byte.
 */
enum WireFormat {

    PROTOBUF,
    BINARY;

    /** The data MIME type of rsocket-java when none is configured. Its payloads are protobuf. */
    static final String PROTOBUF_MIME_TYPE = "application/binary";

    /** The common name of protobuf as a MIME type. */
    static final String PROTOBUF_MIME_TYPE_ALIAS = "application/x-protobuf";

    /**
     * The encoding a data MIME type stands for, or null if the node does not
     * speak it.
     */
    static @Nullable WireFormat ofMimeType(@Nullable String mimeType) {
        if (mimeType == null) {
            return null;
        }
        return switch (mimeType) {
        case PROTOBUF_MIME_TYPE, PROTOBUF_MIME_TYPE_ALIAS -> PROTOBUF;
        case SaplBinaryCodec.MIME_TYPE                    -> BINARY;
        default                                           -> null;
        };
    }

    AuthorizationSubscription readSubscription(byte[] data) throws IOException {
        return switch (this) {
        case PROTOBUF -> SaplProtobufCodec.readAuthorizationSubscription(data);
        case BINARY   -> SaplBinaryCodec.readAuthorizationSubscription(data);
        };
    }

    MultiAuthorizationSubscription readMultiSubscription(byte[] data, int maxSubscriptions) throws IOException {
        return switch (this) {
        case PROTOBUF -> SaplProtobufCodec.readMultiAuthorizationSubscription(data, maxSubscriptions);
        case BINARY   -> SaplBinaryCodec.readMultiAuthorizationSubscription(data, maxSubscriptions);
        };
    }

    byte[] write(AuthorizationDecision decision) throws IOException {
        return switch (this) {
        case PROTOBUF -> SaplProtobufCodec.writeAuthorizationDecision(decision);
        case BINARY   -> SaplBinaryCodec.writeAuthorizationDecision(decision);
        };
    }

    byte[] write(IdentifiableAuthorizationDecision decision) throws IOException {
        return switch (this) {
        case PROTOBUF -> SaplProtobufCodec.writeIdentifiableAuthorizationDecision(decision);
        case BINARY   -> SaplBinaryCodec.writeIdentifiableAuthorizationDecision(decision);
        };
    }

    byte[] write(MultiAuthorizationDecision decision) throws IOException {
        return switch (this) {
        case PROTOBUF -> SaplProtobufCodec.writeMultiAuthorizationDecision(decision);
        case BINARY   -> SaplBinaryCodec.writeMultiAuthorizationDecision(decision);
        };
    }
}
