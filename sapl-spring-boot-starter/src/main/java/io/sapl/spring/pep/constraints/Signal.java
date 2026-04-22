/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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
package io.sapl.spring.pep.constraints;

import io.sapl.api.pdp.AuthorizationDecision;
import org.aopalliance.intercept.MethodInvocation;

public sealed interface Signal permits Signal.VoidSignal, Signal.ValueSignal {
    sealed interface VoidSignal extends Signal
            permits CancelSignal, CompleteSignal, TerminationSignal, AfterTerminationSignal {
    }

    sealed interface ValueSignal<T> extends Signal permits DecisionSignal, InputSignal, OutputSignal, ErrorSignal,
            SubscriptionSignal, R2dbcShimSignal, MongoDbShimSignal {
        T value();

        Class<? extends T> valueType();
    }

    SignalType type();

    record DecisionSignal(AuthorizationDecision value) implements ValueSignal<AuthorizationDecision> {
        private static final SignalType TYPE = new SignalType.ValueSignalType<>(DecisionSignal.class,
                AuthorizationDecision.class);

        @Override
        public Class<? extends AuthorizationDecision> valueType() {
            return AuthorizationDecision.class;
        }

        @Override
        public SignalType type() {
            return TYPE;
        }
    }

    record InputSignal(MethodInvocation value) implements ValueSignal<MethodInvocation> {
        private static final SignalType TYPE = new SignalType.ValueSignalType<>(InputSignal.class,
                MethodInvocation.class);

        @Override
        public Class<? extends MethodInvocation> valueType() {
            return MethodInvocation.class;
        }

        @Override
        public SignalType type() {
            return TYPE;
        }
    }

    record OutputSignal<T>(Class<? extends T> valueType, T value) implements ValueSignal<T> {
        @Override
        @SuppressWarnings({ "unchecked", "rawtypes" })
        public SignalType type() {
            return new SignalType.ValueSignalType<>((Class<? extends ValueSignal<T>>) (Class) OutputSignal.class,
                    valueType);
        }
    }

    record ErrorSignal(Throwable value) implements ValueSignal<Throwable> {
        private static final SignalType TYPE = new SignalType.ValueSignalType<>(ErrorSignal.class, Throwable.class);

        @Override
        public Class<? extends Throwable> valueType() {
            return Throwable.class;
        }

        @Override
        public SignalType type() {
            return TYPE;
        }
    }

    record SubscriptionSignal(Long value) implements ValueSignal<Long> {
        private static final SignalType TYPE = new SignalType.ValueSignalType<>(SubscriptionSignal.class, Long.class);

        @Override
        public Class<? extends Long> valueType() {
            return Long.class;
        }

        @Override
        public SignalType type() {
            return TYPE;
        }
    }

    record CancelSignal() implements VoidSignal {
        private static final SignalType TYPE = new SignalType.VoidSignalType(CancelSignal.class);

        @Override
        public SignalType type() {
            return TYPE;
        }
    }

    record CompleteSignal() implements VoidSignal {
        private static final SignalType TYPE = new SignalType.VoidSignalType(CompleteSignal.class);

        @Override
        public SignalType type() {
            return TYPE;
        }
    }

    record TerminationSignal() implements VoidSignal {
        private static final SignalType TYPE = new SignalType.VoidSignalType(TerminationSignal.class);

        @Override
        public SignalType type() {
            return TYPE;
        }
    }

    record AfterTerminationSignal() implements VoidSignal {
        private static final SignalType TYPE = new SignalType.VoidSignalType(AfterTerminationSignal.class);

        @Override
        public SignalType type() {
            return TYPE;
        }
    }

    // TODO: determine correct value type
    record R2dbcShimSignal(Object value) implements ValueSignal<Object> {
        private static final SignalType TYPE = new SignalType.ValueSignalType<>(R2dbcShimSignal.class, Object.class);

        @Override
        public Class<?> valueType() {
            return Object.class;
        }

        @Override
        public SignalType type() {
            return TYPE;
        }
    }

    // TODO: determine correct value type
    record MongoDbShimSignal(Object value) implements ValueSignal<Object> {
        private static final SignalType TYPE = new SignalType.ValueSignalType<>(MongoDbShimSignal.class, Object.class);

        @Override
        public Class<?> valueType() {
            return Object.class;
        }

        @Override
        public SignalType type() {
            return TYPE;
        }
    }

}
