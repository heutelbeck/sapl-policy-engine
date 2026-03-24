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

import com.google.flatbuffers.FlatBufferBuilder;
import com.google.flatbuffers.Table;

/**
 * FlatBuffers table for Value - the central value type.
 */
class FbValue extends Table {

    FbValue __assign(int i, ByteBuffer bb) {
        __reset(i, bb);
        return this;
    }

    byte kindType() {
        int o = __offset(4);
        return o != 0 ? bb.get(o + bb_pos) : 0;
    }

    Table kind(Table obj) {
        int o = __offset(6);
        return o != 0 ? __union(obj, o + bb_pos) : null;
    }

    static void startFbValue(FlatBufferBuilder builder) {
        builder.startTable(2);
    }

    static void addKindType(FlatBufferBuilder builder, byte kindType) {
        builder.addByte(0, kindType, 0);
    }

    static void addKind(FlatBufferBuilder builder, int kindOffset) {
        builder.addOffset(1, kindOffset, 0);
    }

    static int endFbValue(FlatBufferBuilder builder) {
        return builder.endTable();
    }
}
