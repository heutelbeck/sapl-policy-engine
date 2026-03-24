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
 * FlatBuffers table for BoolValue.
 */
class FbBoolValue extends Table {

    FbBoolValue __assign(int i, ByteBuffer bb) {
        __reset(i, bb);
        return this;
    }

    boolean value() {
        int o = __offset(4);
        return o != 0 && bb.get(o + bb_pos) != 0;
    }

    static int createFbBoolValue(FlatBufferBuilder builder, boolean value) {
        builder.startTable(1);
        builder.addBoolean(0, value, false);
        return builder.endTable();
    }
}
