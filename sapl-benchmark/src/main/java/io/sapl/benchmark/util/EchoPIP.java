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
package io.sapl.benchmark.util;

import java.time.Duration;

import io.sapl.api.attributes.Attribute;
import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import reactor.core.publisher.Flux;

@PolicyInformationPoint(name = "echo", description = "PIP echoing the input value after 0,5 seconds")
public class EchoPIP {
    @Attribute(name = "delayed")
    public Flux<Value> delayed(TextValue value) {
        return Flux.just((Value) value).delayElements(Duration.ofMillis(500));
    }

}
