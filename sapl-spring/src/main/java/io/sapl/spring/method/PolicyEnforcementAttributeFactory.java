/**
 * Copyright © 2019 Dominic Heutelbeck (dominic.heutelbeck@gmail.com)
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
package io.sapl.spring.method;

import org.springframework.aop.framework.AopInfrastructureBean;

import io.sapl.spring.method.post.PostInvocationEnforcementAttribute;
import io.sapl.spring.method.pre.PreInvocationEnforcementAttribute;

public interface PolicyEnforcementAttributeFactory extends AopInfrastructureBean {

	PreInvocationEnforcementAttribute createPreInvocationAttribute(String subjectAttribute, String actionAttribute,
			String resourceAttribute, String environmentAttribute);

	PostInvocationEnforcementAttribute createPostInvocationAttribute(String subjectAttribute, String actionAttribute,
			String resourceAttribute, String environmentAttribute);

}
