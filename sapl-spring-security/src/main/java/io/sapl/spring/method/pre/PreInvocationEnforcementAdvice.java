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
package io.sapl.spring.method.pre;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.security.core.Authentication;

/**
 * Performs policy enforcement and authorization logic before a method is
 * invoked.
 */
public interface PreInvocationEnforcementAdvice extends AopInfrastructureBean {

	/**
	 * The "before" advice which should be executed to perform policy enforcement to
	 * decide whether the method call is authorized. Required obligations and
	 * available advices are executed.
	 * 
	 * @param authentication         the information on the principal on whose
	 *                               account the decision should be made
	 * @param mi                     the method invocation being attempted
	 * @param preInvocationAttribute the attribute built from the @PreEnforce
	 *                               annotation.
	 * @return true if authorized and obligations fulfilled, false otherwise
	 */
	boolean before(Authentication authentication, MethodInvocation mi,
			PolicyBasedPreInvocationEnforcementAttribute preInvocationAttribute);

}
