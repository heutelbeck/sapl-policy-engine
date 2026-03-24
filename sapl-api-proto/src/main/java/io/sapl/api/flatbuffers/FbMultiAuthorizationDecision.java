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
 * FlatBuffers table for MultiAuthorizationDecision.
 */
class FbMultiAuthorizationDecision extends Table {

    static FbMultiAuthorizationDecision getRootAsFbMultiAuthorizationDecision(ByteBuffer bb) {
        bb.order(ByteOrder.LITTLE_ENDIAN);
        return new FbMultiAuthorizationDecision().__assign(bb.getInt(bb.position()) + bb.position(), bb);
    }

    FbMultiAuthorizationDecision __assign(int i, ByteBuffer bb) {
        __reset(i, bb);
        return this;
    }

    FbDecisionEntry decisions(int j) {
        int o = __offset(4);
        return o != 0 ? new FbDecisionEntry().__assign(__indirect(__vector(o) + j * 4), bb) : null;
    }

    int decisionsLength() {
        int o = __offset(4);
        return o != 0 ? __vector_len(o) : 0;
    }

    static int createFbMultiAuthorizationDecision(FlatBufferBuilder builder, int decisionsOffset) {
        builder.startTable(1);
        builder.addOffset(0, decisionsOffset, 0);
        return builder.endTable();
    }

    static int createDecisionsVector(FlatBufferBuilder builder, int[] data) {
        builder.startVector(4, data.length, 4);
        for (int i = data.length - 1; i >= 0; i--) {
            builder.addOffset(data[i]);
        }
        return builder.endVector();
    }
}
