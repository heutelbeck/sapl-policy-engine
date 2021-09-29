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
package io.sapl.spring.method.blocking;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.security.core.Authentication;

import io.sapl.spring.method.attributes.PostEnforceAttribute;

/**
 * Performs policy enforcement and authorization logic after a method is
 * invoked.
 */
public interface PostInvocationEnforcementAdvice extends AopInfrastructureBean {

	Object after(Authentication authentication, MethodInvocation mi, PostEnforceAttribute pia,
			Object returnedObject);

}
