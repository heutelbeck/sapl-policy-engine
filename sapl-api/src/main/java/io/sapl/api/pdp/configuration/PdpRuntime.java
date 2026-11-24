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
package io.sapl.api.pdp.configuration;

import java.util.Arrays;
import java.util.Optional;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The implementations of the SAPL engine a PDP configuration can expect to run
 * on. The engines agree on the decisions of policies, and they differ in a few
 * documented cases, such as the keys of {@code patternProperties} a JSON Schema
 * may hold. A configuration that depends on such a case names the runtimes it
 * was written for.
 */
@Getter
@RequiredArgsConstructor
public enum PdpRuntime {

    /** The engine written in Java, which this library is. */
    JAVA("java"),

    /** The engine written in Rust. */
    RUST("rust");

    /** The runtime of the engine that runs this code. */
    public static final PdpRuntime RUNNING = JAVA;

    private final String id;

    /**
     * @param id the name of a runtime as a configuration writes it
     * @return the runtime, or empty if the name names none
     */
    public static Optional<PdpRuntime> ofId(String id) {
        return Arrays.stream(values()).filter(runtime -> runtime.id.equals(id)).findFirst();
    }

}
