/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.spring.method.attributes;

import org.springframework.aop.framework.AopInfrastructureBean;

import io.sapl.spring.method.annotations.EnforcementMode;

public interface SaplEnforcementAttributeFactory extends AopInfrastructureBean {

	PreInvocationEnforcementAttribute createPreEnforceAttribute(String subjectAttribute, String actionAttribute,
			String resourceAttribute, String environmentAttribute, Class<?> genericsType);

	PostInvocationEnforcementAttribute createPostEnforceAttribute(String subjectAttribute, String actionAttribute,
			String resourceAttribute, String environmentAttribute, Class<?> genericsType);

	EnforceAttribute createEnforceAttribute(String subjectAttribute, String actionAttribute, String resourceAttribute,
			String environemntAttribute, EnforcementMode modeAttribute, Class<?> genericsType);

}
