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
package io.sapl.spring.method.reactive;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.spring.constraints.ReactiveConstraintHandlerBundle;

/**
 * Composite state holding both the authorization decision and its corresponding
 * constraint handler bundle atomically. This eliminates a race condition where
 * separate reads of decision and bundle could observe inconsistent state when a
 * new decision arrives on a different thread.
 *
 * @param <T> type of the Flux contents
 * @param decision the current authorization decision
 * @param bundle the constraint handler bundle matching the decision
 */
record EnforcementState<T>(AuthorizationDecision decision, ReactiveConstraintHandlerBundle<T> bundle) {}
