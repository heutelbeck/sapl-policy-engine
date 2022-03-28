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

package io.sapl.axon.client.metadata;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.MessageDispatchInterceptor;

/**
 * Abstract implementation of a {@link MessageDispatchInterceptor} that
 * delegates Commands to a SaplCommandInterceptor. Inside this abstract
 * implementation the metadata regarding the subject are merged to the Command
 * Message, before the Command is dispatched.
 */

public abstract class AbstractSaplCommandInterceptor implements MessageDispatchInterceptor<CommandMessage<?>> {

	protected abstract Map<String, Object> getSubjectMetadata();

	@Override
	public CommandMessage<?> handle(CommandMessage<?> message) {
		var metadata = getSubjectMetadata();
		if(metadata != null)
			if (!metadata.isEmpty())
				message = message.andMetaData(metadata);

		return MessageDispatchInterceptor.super.handle(message);
	}

	@Override
	public BiFunction<Integer, CommandMessage<?>, CommandMessage<?>> handle(
			List<? extends CommandMessage<?>> messages) {
		return (i, m) -> m;
	}
}
