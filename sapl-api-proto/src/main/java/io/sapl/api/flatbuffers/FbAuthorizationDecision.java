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
 * FlatBuffers table for AuthorizationDecision.
 */
class FbAuthorizationDecision extends Table {

    static FbAuthorizationDecision getRootAsFbAuthorizationDecision(ByteBuffer bb) {
        bb.order(ByteOrder.LITTLE_ENDIAN);
        return new FbAuthorizationDecision().__assign(bb.getInt(bb.position()) + bb.position(), bb);
    }

    FbAuthorizationDecision __assign(int i, ByteBuffer bb) {
        __reset(i, bb);
        return this;
    }

    byte decision() {
        int o = __offset(4);
        return o != 0 ? bb.get(o + bb_pos) : 0;
    }

    FbArrayValue obligations() {
        int o = __offset(6);
        return o != 0 ? new FbArrayValue().__assign(__indirect(o + bb_pos), bb) : null;
    }

    FbArrayValue advice() {
        int o = __offset(8);
        return o != 0 ? new FbArrayValue().__assign(__indirect(o + bb_pos), bb) : null;
    }

    FbValue resource() {
        int o = __offset(10);
        return o != 0 ? new FbValue().__assign(__indirect(o + bb_pos), bb) : null;
    }

    static int createFbAuthorizationDecision(FlatBufferBuilder builder, byte decision, int obligationsOffset,
            int adviceOffset, int resourceOffset) {
        builder.startTable(4);
        builder.addByte(0, decision, 0);
        builder.addOffset(1, obligationsOffset, 0);
        builder.addOffset(2, adviceOffset, 0);
        builder.addOffset(3, resourceOffset, 0);
        return builder.endTable();
    }
}
