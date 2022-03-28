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
package io.sapl.axon.commandhandling.disruptor;

import org.axonframework.disruptor.commandhandling.CommandHandlingEntry;

import com.lmax.disruptor.dsl.Disruptor;

import lombok.extern.slf4j.Slf4j;

/**
 * This implementation handles exception from the {@link Disruptor} and logs the exception.
 * In case of an exception during LifecycleAware.onStart() the Disruptor will be shutdown.
 */
@Slf4j
class DisruptorExceptionHandler implements com.lmax.disruptor.ExceptionHandler<Object> {

	private final Disruptor<?> disruptor;

	/**
	 * Constructor with a Disruptor parameter
	 * 
	 * @param disruptor Disruptor
	 */
	DisruptorExceptionHandler(Disruptor<?> disruptor) {
		this.disruptor = disruptor;
	}

	@Override
    public void handleEventException(Throwable ex, long sequence, Object event) {
        log.error("Exception occurred while processing a {}.",
                     ((CommandHandlingEntry) event).getMessage().getPayloadType().getSimpleName(), ex);
    }

    @Override
    public void handleOnStartException(Throwable ex) {
        log.error("Failed to start the SaplDisruptorCommandBus.", ex);
        this.disruptor.shutdown();
    }

    @Override
    public void handleOnShutdownException(Throwable ex) {
    	log.error("Error while shutting down the SaplDisruptorCommandBus", ex);
    }
}