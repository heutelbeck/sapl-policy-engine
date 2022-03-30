/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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

package io.sapl.axon.async;

import java.util.Arrays;
import java.util.List;

import org.axonframework.messaging.annotation.HandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.queryhandling.annotation.QueryHandlingMember;

import io.sapl.axon.queryhandling.QueryPolicyEnforcementPoint;
import lombok.RequiredArgsConstructor;

/**
 * Default Implementation of Axon HandlerEnhancer which allows the enhancing of
 * QueryHandlingMember with the {@link DefaultSAPLQueryHandlingMember}.
 *
 */

@RequiredArgsConstructor
public class DefaultSAPLQueryHandlerEnhancer implements HandlerEnhancerDefinition {

	private final QueryPolicyEnforcementPoint pep;

	@Override
	public <T> MessageHandlingMember<T> wrapHandler(MessageHandlingMember<T> original) {

		// Determine which HandlingMember is to be enhanced
		List<Class<?>> interfaces = Arrays.asList(original.getClass().getInterfaces());

		// Enhancing the workflow to execute the DefaultSAPLQueryHandlingMember as a
		// Wrapper of QueryMessages
		if (interfaces.contains(QueryHandlingMember.class)) {
			return new DefaultSAPLQueryHandlingMember<>(original, pep);
		}
		return original;
	}

}
