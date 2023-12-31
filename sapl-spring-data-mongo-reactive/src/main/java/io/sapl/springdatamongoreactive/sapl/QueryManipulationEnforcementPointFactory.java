/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.springdatamongoreactive.sapl;

import org.springframework.stereotype.Service;

import io.sapl.springdatamongoreactive.sapl.queries.enforcement.MongoAnnotationQueryManipulationEnforcementPoint;
import io.sapl.springdatamongoreactive.sapl.queries.enforcement.MongoMethodNameQueryManipulationEnforcementPoint;
import io.sapl.springdatamongoreactive.sapl.queries.enforcement.ProceededDataFilterEnforcementPoint;

@Service
public class QueryManipulationEnforcementPointFactory {

    public <T> QueryManipulationEnforcementPoint<T> createMongoAnnotationQueryManipulationEnforcementPoint(
            QueryManipulationEnforcementData<T> enforcementData) {
        return new MongoAnnotationQueryManipulationEnforcementPoint<>(enforcementData);
    }

    public <T> QueryManipulationEnforcementPoint<T> createMongoMethodNameQueryManipulationEnforcementPoint(
            QueryManipulationEnforcementData<T> enforcementData) {
        return new MongoMethodNameQueryManipulationEnforcementPoint<>(enforcementData);
    }

    public <T> QueryManipulationEnforcementPoint<T> createProceededDataFilterEnforcementPoint(
            QueryManipulationEnforcementData<T> enforcementData) {
        return new ProceededDataFilterEnforcementPoint<>(enforcementData);
    }

}
