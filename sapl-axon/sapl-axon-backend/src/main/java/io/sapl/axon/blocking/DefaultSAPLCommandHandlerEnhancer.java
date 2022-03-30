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
package io.sapl.axon.blocking;

import java.util.Arrays;

import org.axonframework.commandhandling.CommandMessageHandlingMember;
import org.axonframework.messaging.annotation.HandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.MessageHandlingMember;

import io.sapl.axon.commandhandling.CommandPolicyEnforcementPoint;
import lombok.RequiredArgsConstructor;

/**
 * Default Implementation of Axon HandlerEnhancer which allows the enhancing of
 * CommandHandlingMember with the {@link DefaultSAPLCommandHandlingMember}.
 *
 */
@RequiredArgsConstructor
public class DefaultSAPLCommandHandlerEnhancer implements HandlerEnhancerDefinition {

	private final CommandPolicyEnforcementPoint pep;

	@Override
	public <T> MessageHandlingMember<T> wrapHandler(MessageHandlingMember<T> original) {

		// Determine which HandlingMember is to be enhanced
		var interfaces = Arrays.asList(original.getClass().getInterfaces());

		// Enhancing the workflow to execute the DefaultSAPLCommandHandlingMember as a
		// Wrapper of CommandMessages
		if (interfaces.contains(CommandMessageHandlingMember.class)) {
			return new DefaultSAPLCommandHandlingMember<>(original, pep);
		}

		return original;
	}
}
