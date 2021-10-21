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

import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.beans.factory.ObjectFactory;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.subscriptions.AuthorizationSubscriptionBuilderService;
import lombok.RequiredArgsConstructor;

/**
 * Base logic for PreEnforce and PostEnforce advice classes. Primarily takes
 * care of lazy loading dependencies.
 */
@RequiredArgsConstructor
public abstract class AbstractPolicyEnforcementPoint implements AopInfrastructureBean {

	protected final ObjectFactory<PolicyDecisionPoint> pdpFactory;
	protected final ObjectFactory<ConstraintEnforcementService> constraintEnforcementServiceFactory;
	protected final ObjectFactory<AuthorizationSubscriptionBuilderService> subscriptionBuilderFactory;
	protected PolicyDecisionPoint pdp;
	protected ConstraintEnforcementService constraintEnforcementService;
	protected AuthorizationSubscriptionBuilderService subscriptionBuilder;

	/**
	 * Lazy loading of dependencies decouples security infrastructure from domain
	 * logic in initialization. This avoids beans to become not eligible for Bean
	 * post processing.
	 */
	protected void lazyLoadDependencies() {
		if (pdp == null)
			pdp = pdpFactory.getObject();

		if (constraintEnforcementService == null)
			constraintEnforcementService = constraintEnforcementServiceFactory.getObject();

		if (subscriptionBuilder == null)
			subscriptionBuilder = subscriptionBuilderFactory.getObject();
	}

}
