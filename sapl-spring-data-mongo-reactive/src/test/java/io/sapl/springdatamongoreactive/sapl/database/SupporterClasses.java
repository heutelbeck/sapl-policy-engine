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
package io.sapl.springdatamongoreactive.sapl.database;

import io.sapl.springdatamongoreactive.sapl.QueryManipulationEnforcementData;
import io.sapl.springdatamongoreactive.sapl.handlers.DataManipulationHandler;
import io.sapl.springdatamongoreactive.sapl.querytypes.methodnameenforcement.SaplPartTreeCriteriaCreator;
import io.sapl.springdatamongoreactive.sapl.querytypes.annotationenforcement.MongoAnnotationQueryManipulationEnforcementPoint;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;

public class SupporterClasses {

    public static class DataManipulationHandlerTestUser extends DataManipulationHandler<TestUser> {

        public DataManipulationHandlerTestUser(Class<TestUser> domainType) {
            super(domainType);
            // TODO Auto-generated constructor stub
        }

    }

    public static class SaplPartTreeCriteriaCreatorTestUser extends SaplPartTreeCriteriaCreator<TestUser> {

        public SaplPartTreeCriteriaCreatorTestUser(ReactiveMongoTemplate reactiveMongoTemplate,
                MethodInvocation methodInvocation, Class<TestUser> domainType) {
            super(reactiveMongoTemplate, methodInvocation, domainType);
            // TODO Auto-generated constructor stub
        }

    }

    public static class MongoAnnotationQueryManipulationEnforcementPointTestUser
            extends MongoAnnotationQueryManipulationEnforcementPoint<TestUser> {

        public MongoAnnotationQueryManipulationEnforcementPointTestUser(
                QueryManipulationEnforcementData<TestUser> enforcementData) {
            super(enforcementData);
            // TODO Auto-generated constructor stub
        }

    }
}
