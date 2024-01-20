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
package io.sapl.springdatar2dbc.sapl;

import org.springframework.stereotype.Service;

import io.sapl.springdatacommon.sapl.QueryManipulationEnforcementData;
import io.sapl.springdatacommon.sapl.QueryManipulationEnforcementPoint;
import io.sapl.springdatacommon.sapl.queries.enforcement.ProceededDataFilterEnforcementPoint;
import io.sapl.springdatar2dbc.sapl.queries.enforcement.R2dbcAnnotationQueryManipulationEnforcementPoint;
import io.sapl.springdatar2dbc.sapl.queries.enforcement.R2dbcMethodNameQueryManipulationEnforcementPoint;

@Service
public class QueryManipulationEnforcementPointFactory {

    public <T> QueryManipulationEnforcementPoint<T> createR2dbcAnnotationQueryManipulationEnforcementPoint(
            QueryManipulationEnforcementData<T> enforcementData) {
        return new R2dbcAnnotationQueryManipulationEnforcementPoint<>(enforcementData);
    }

    public <T> QueryManipulationEnforcementPoint<T> createR2dbcMethodNameQueryManipulationEnforcementPoint(
            QueryManipulationEnforcementData<T> enforcementData) {
        return new R2dbcMethodNameQueryManipulationEnforcementPoint<>(enforcementData);
    }

    public <T> QueryManipulationEnforcementPoint<T> createProceededDataFilterEnforcementPoint(
            QueryManipulationEnforcementData<T> enforcementData) {
        return new ProceededDataFilterEnforcementPoint<>(enforcementData, true);
    }

}
