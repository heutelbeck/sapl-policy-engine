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
package io.sapl.pdp.configuration;

import io.sapl.compiler.pdp.CompiledPdpVoter;

/**
 * Notification dispatched by {@link PdpVoterSource} when the configuration
 * for a pdpId changes. Mirrors the upstream
 * {@link io.sapl.pdp.configuration.source.PDPConfigurationSource.ConfigurationEvent}
 * shape: a sealed sum type lets consumers exhaustively pattern-match on
 * the change kind without nullability ambiguity.
 */
public sealed interface PdpUpdateEvent {

    /**
     * The pdpId the event refers to.
     *
     * @return the PDP identifier
     */
    String pdpId();

    /**
     * The PDP gained or replaced its compiled voter.
     *
     * @param pdpId the PDP identifier
     * @param voter the newly compiled voter (which may be an error voter
     * created from a failing compilation when keepOldConfigOnError was set)
     */
    record Voter(String pdpId, CompiledPdpVoter voter) implements PdpUpdateEvent {}

    /**
     * The PDP's configuration was removed.
     *
     * @param pdpId the PDP identifier
     */
    record Removed(String pdpId) implements PdpUpdateEvent {}
}
