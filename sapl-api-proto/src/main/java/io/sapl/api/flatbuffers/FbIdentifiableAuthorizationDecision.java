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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.google.flatbuffers.FlatBufferBuilder;
import com.google.flatbuffers.Table;

/**
 * FlatBuffers table for IdentifiableAuthorizationDecision.
 */
class FbIdentifiableAuthorizationDecision extends Table {

    static FbIdentifiableAuthorizationDecision getRootAsFbIdentifiableAuthorizationDecision(ByteBuffer bb) {
        bb.order(ByteOrder.LITTLE_ENDIAN);
        return new FbIdentifiableAuthorizationDecision().__assign(bb.getInt(bb.position()) + bb.position(), bb);
    }

    FbIdentifiableAuthorizationDecision __assign(int i, ByteBuffer bb) {
        __reset(i, bb);
        return this;
    }

    String subscriptionId() {
        int o = __offset(4);
        return o != 0 ? __string(o + bb_pos) : null;
    }

    FbAuthorizationDecision decision() {
        int o = __offset(6);
        return o != 0 ? new FbAuthorizationDecision().__assign(__indirect(o + bb_pos), bb) : null;
    }

    static int createFbIdentifiableAuthorizationDecision(FlatBufferBuilder builder, int subscriptionIdOffset,
            int decisionOffset) {
        builder.startTable(2);
        builder.addOffset(0, subscriptionIdOffset, 0);
        builder.addOffset(1, decisionOffset, 0);
        return builder.endTable();
    }
}
