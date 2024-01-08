/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.springdatacommon.sapl.utils;

import org.bson.types.ObjectId;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

public class OidObjectMapper extends ObjectMapper {

    /**
     *
     */
    private static final long serialVersionUID = -1238167469457710403L;

    /**
     * A modified ObjectMapper to handle {@link ObjectId}s.
     */
    public OidObjectMapper() {
        var module = new SimpleModule("OidModule");
        module.addSerializer(ObjectId.class, new OidSerializer());
        this.registerModule(module);
    }

}
