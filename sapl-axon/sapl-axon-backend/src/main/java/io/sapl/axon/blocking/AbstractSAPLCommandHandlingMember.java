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

import java.lang.reflect.Executable;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.WrappedMessageHandlingMember;

import io.sapl.axon.commandhandling.CommandPolicyEnforcementPoint;
import io.sapl.spring.method.metadata.PreEnforce;


/**
 * Abstract implementation of a {@link MessageHandlingMember} that delegates
 * Commands to a wrapped MessageHandlingMember. Inside this abstract
 * implementation the {@link CommandPolicyEnforcementPoint} is called to handle
 * the authorization of the commands. {@link PreEnforce} annotation on the
 * CommandHandler will be enforced and the Constraints will be handled before
 * and after CommandHandling. Extend this class to provide additional
 * functionality to the delegate member.
 *
 */

abstract public class AbstractSAPLCommandHandlingMember<T> extends WrappedMessageHandlingMember<T> {

	private final MessageHandlingMember<T> delegate;

	private final CommandPolicyEnforcementPoint pep;

	protected AbstractSAPLCommandHandlingMember(MessageHandlingMember<T> delegate, CommandPolicyEnforcementPoint pep) {
		super(delegate);
		this.delegate = delegate;
		this.pep = pep;
	}

	@Override
	public Object handle(Message<?> message, T aggregate) throws Exception {
		if (isHandlerPreEnforced()) {
			return handlePreEnforced(message, aggregate);
		} else {
			return super.handle(message, aggregate);
		}
	}

	private boolean isHandlerPreEnforced() {
		var optional = delegate.unwrap(Executable.class);
		return optional.orElseThrow().isAnnotationPresent(PreEnforce.class);
	}

	private <Q> Object handlePreEnforced(Message<Q> message, T aggregate) throws Exception {
		return pep.preEnforceCommandBlocking((CommandMessage<Q>) message, aggregate, delegate, super::handle);
	}

}
