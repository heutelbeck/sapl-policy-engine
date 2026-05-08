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
package io.sapl.server.pdpcontroller;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.reactive.api.pdp.PolicyDecisionPoint;
import io.sapl.reactive.api.tenant.ReactiveTenantResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * REST controller providing endpoints for a policy decision point. The
 * endpoints can be connected using the client in the module sapl-pdp-client.
 */

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/pdp")
@Tag(name = "SAPL native", description = "Full SAPL PDP HTTP surface: one-shot decisions, server-sent-event streams, multi-decision boxcars, with the five-valued decision verb and obligations / advice / resource transformation.")
public class PDPController {
    private final PolicyDecisionPoint    pdp;
    private final ReactiveTenantResolver tenantResolver;
    @Value("#{'${io.sapl.server.keep-alive:${io.sapl.node.keep-alive:0}}'}")
    private long                         keepAliveSeconds = 0;

    /**
     * Enables keep alive comments to keep tcp connection active. This is usually
     * needed to avoid that connections are dropped by firewalls.
     *
     * @param flux a flux emitting the authorization decisions
     * @return the original flux with additional keep-alive messages if keep-alive
     * parameter > 0
     */
    private <T> Flux<ServerSentEvent<T>> wrapWithKeepAlive(Flux<T> flux) {
        if (keepAliveSeconds > 0) {
            return Flux.merge(flux.map(t -> ServerSentEvent.builder(t).build()),
                    Flux.interval(Duration.ofSeconds(this.keepAliveSeconds))
                            .map(aLong -> ServerSentEvent.<T>builder().comment("keep-alive").build()));
        } else {
            return flux.map(decision -> ServerSentEvent.<T>builder().data(decision).build());
        }
    }

    /**
     * Delegates to {@link PolicyDecisionPoint#decide(AuthorizationSubscription)}.
     *
     * @param authzSubscription the authorization subscription to be processed by
     * the PDP.
     * @return a flux emitting the current authorization decisions.
     * @see PolicyDecisionPoint#decide(AuthorizationSubscription)
     */
    @Operation(summary = "Subscribe to a stream of authorization decisions", description = "Returns a server-sent-event stream of AuthorizationDecision values. Each event represents an updated decision, re-emitted whenever the underlying authorization context changes (attribute updates, policy changes). The connection stays open until the client disconnects.")
    @PostMapping(value = "/decide", produces = MediaType.TEXT_EVENT_STREAM_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public Flux<ServerSentEvent<AuthorizationDecision>> decide(
            @Valid @RequestBody AuthorizationSubscription authzSubscription) {
        return wrapWithKeepAlive(tenantResolver.resolve().flatMapMany(pdpId -> pdp.decide(authzSubscription, pdpId))
                .onErrorResume(error -> {
                    log.error("Error during authorization decision for subscription {}: {}", authzSubscription,
                            error.getMessage(), error);
                    return Flux.just(AuthorizationDecision.INDETERMINATE);
                }));
    }

    /**
     * Delegates to
     * {@link PolicyDecisionPoint#decideOnce(AuthorizationSubscription)}.
     *
     * @param authzSubscription the authorization subscription to be processed by
     * the PDP.
     * @return a Mono for the initial decision.
     * @see PolicyDecisionPoint#decideOnce(AuthorizationSubscription)
     */
    @Operation(summary = "Get a single authorization decision", description = "Returns the first authorization decision for the subscription. Empty upstream is mapped to INDETERMINATE.")
    @PostMapping(value = "/decide-once", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<AuthorizationDecision> decideOnce(@Valid @RequestBody AuthorizationSubscription authzSubscription) {
        return tenantResolver.resolve().flatMap(pdpId -> pdp.decideOnce(authzSubscription, pdpId))
                .onErrorReturn(AuthorizationDecision.INDETERMINATE);
    }

    /**
     * Delegates to
     * {@link PolicyDecisionPoint#decide(MultiAuthorizationSubscription)}.
     *
     * @param multiAuthzSubscription the authorization multi-subscription to be
     * processed by the PDP.
     * @return a flux emitting authorization decisions related to the individual
     * subscriptions contained in the given {@code multiAuthzSubscription} as soon
     * as they are available.
     * @see PolicyDecisionPoint#decide(MultiAuthorizationSubscription)
     */
    @Operation(summary = "Subscribe to a stream of identifiable decisions for many subscriptions", description = "Server-sent-event stream of IdentifiableAuthorizationDecision values: one event per (subscription id, decision) pair, emitted as decisions become available or change.")
    @PostMapping(value = "/multi-decide", produces = MediaType.TEXT_EVENT_STREAM_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public Flux<ServerSentEvent<IdentifiableAuthorizationDecision>> decide(
            @Valid @RequestBody MultiAuthorizationSubscription multiAuthzSubscription) {
        return wrapWithKeepAlive(
                tenantResolver.resolve().flatMapMany(pdpId -> pdp.decide(multiAuthzSubscription, pdpId))
                        .onErrorResume(error -> Flux.just(IdentifiableAuthorizationDecision.INDETERMINATE)));
    }

    /**
     * Delegates to
     * {@link PolicyDecisionPoint#decideAll(MultiAuthorizationSubscription)}.
     *
     * @param multiAuthzSubscription the authorization multi-subscription to be
     * processed by the PDP.
     * @return a flux emitting multi-decisions containing authorization decisions
     * for all the individual authorization subscriptions contained in the given
     * {@code multiAuthzSubscription}.
     * @see PolicyDecisionPoint#decideAll(MultiAuthorizationSubscription)
     */
    @Operation(summary = "Subscribe to a stream of bundled multi-decisions", description = "Server-sent-event stream of MultiAuthorizationDecision values: each event carries the current decision for every subscription in the request.")
    @PostMapping(value = "/multi-decide-all", produces = MediaType.TEXT_EVENT_STREAM_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public Flux<ServerSentEvent<MultiAuthorizationDecision>> decideAll(
            @Valid @RequestBody MultiAuthorizationSubscription multiAuthzSubscription) {
        return wrapWithKeepAlive(
                tenantResolver.resolve().flatMapMany(pdpId -> pdp.decideAll(multiAuthzSubscription, pdpId))
                        .onErrorResume(error -> Flux.just(MultiAuthorizationDecision.indeterminate())));
    }

    /**
     * Delegates to
     * {@link PolicyDecisionPoint#decideAll(MultiAuthorizationSubscription)}.
     *
     * @param multiAuthzSubscription the authorization multi-subscription to be
     * processed by the PDP.
     * @return a Mono emitting the initial multi-decision containing authorization
     * decisions for all the individual authorization subscriptions contained in the
     * given {@code multiAuthzSubscription}.
     * @see PolicyDecisionPoint#decideAll(MultiAuthorizationSubscription)
     */
    @Operation(summary = "Get a single bundled multi-decision", description = "Returns the first MultiAuthorizationDecision for the multi-subscription, with one decision per included subscription id.")
    @PostMapping(value = "/multi-decide-all-once", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<MultiAuthorizationDecision> decideAllOnce(
            @Valid @RequestBody MultiAuthorizationSubscription multiAuthzSubscription) {
        return tenantResolver.resolve().flatMapMany(pdpId -> pdp.decideAll(multiAuthzSubscription, pdpId))
                .onErrorResume(error -> Flux.just(MultiAuthorizationDecision.indeterminate())).next()
                .defaultIfEmpty(MultiAuthorizationDecision.indeterminate());
    }

}
