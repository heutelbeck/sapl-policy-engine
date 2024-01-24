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

import java.util.Objects;

import io.sapl.springdatacommon.sapl.QueryManipulationEnforcementData;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@UtilityClass
public class HandleProceedingData {

    @SneakyThrows // throwable by proceed() method
    @SuppressWarnings("unchecked") // casting Object to Flux<T> or Mono<T>, proceed() returns either Flux<T> or
                                   // Mono<T>
    public static <T> Flux<T> proceed(QueryManipulationEnforcementData<T> enforcementData) {

        if (enforcementData.getMethodInvocation().getMethod().getReturnType().equals(Mono.class)) {
            return Flux.from((Mono<T>) Objects.requireNonNull(enforcementData.getMethodInvocation().proceed()));
        }

        return (Flux<T>) enforcementData.getMethodInvocation().proceed();
    }

}
