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
package io.sapl.spring.pep.http.servlet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.spring.pep.constraints.ConstraintHandler;
import io.sapl.spring.pep.constraints.ConstraintHandlerProvider;
import io.sapl.spring.pep.constraints.EnforcementPlan;
import io.sapl.spring.pep.constraints.EnforcementPlanner;
import io.sapl.spring.pep.constraints.ScopedConstraintHandler;
import io.sapl.spring.pep.constraints.Signal;
import io.sapl.spring.pep.constraints.SignalType;
import io.sapl.spring.pep.constraints.providers.ConstraintResponsibility;
import io.sapl.spring.pep.http.HttpEnforcementContext;
import io.sapl.spring.pep.http.MutableHttpRequest;
import io.sapl.spring.pep.http.MutableHttpResponse;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.val;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("SaplHttpPepFilter")
class SaplHttpPepFilterTests {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private static final Set<SignalType> SUPPORTED_SIGNALS = Set.of(Signal.DecisionSignal.SIGNAL_TYPE,
            Signal.HttpRequestSignal.SIGNAL_TYPE, Signal.HttpRequestMutationSignal.SIGNAL_TYPE,
            Signal.HttpResponseSignal.SIGNAL_TYPE);

    private final SaplHttpPepFilter filter = new SaplHttpPepFilter();

    @Nested
    @DisplayName("Pass-through behaviour")
    class PassThrough {

        @Test
        @DisplayName("when no plan is present, the chain runs against the original request and response")
        void noPlan() throws Exception {
            val request  = new MockHttpServletRequest("GET", "/r");
            val response = new MockHttpServletResponse();
            val chain    = new RecordingFilterChain((req, res) -> {
                             ((HttpServletResponse) res).setStatus(200);
                             res.getWriter().write("body");
                         });
            filter.doFilter(request, response, chain);
            assertThat(chain.request()).isSameAs(request);
            assertThat(chain.response()).isSameAs(response);
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentAsString()).isEqualTo("body");
        }

        @Test
        @DisplayName("plan with no HTTP signal handlers: chain bypasses the wrappers entirely")
        void planWithoutHttpHandlersBypassesWrappers() throws Exception {
            val plan     = planFor(permitWith("audit"), runnerProvider());
            val request  = requestFor(plan);
            val response = new MockHttpServletResponse();
            val chain    = new RecordingFilterChain((req, res) -> {
                             ((HttpServletResponse) res).setStatus(200);
                             res.getWriter().write("body");
                         });
            filter.doFilter(request, response, chain);
            assertThat(chain.request()).isSameAs(request);
            assertThat(chain.response()).isSameAs(response);
            assertThat(response.getContentAsString()).isEqualTo("body");
        }

        @Test
        @DisplayName("only response handlers scheduled: chain receives the original request, wrapped response")
        void onlyResponseHandlersBypassRequestWrapper() throws Exception {
            val plan     = planFor(permitWith("stamp"), responseStampProvider());
            val request  = requestFor(plan);
            val response = new MockHttpServletResponse();
            val chain    = new RecordingFilterChain((req, res) -> ((HttpServletResponse) res).setStatus(200));
            filter.doFilter(request, response, chain);
            assertThat(chain.request()).isSameAs(request);
            assertThat(chain.response()).isInstanceOf(ServletMutableHttpResponse.class);
        }

        @Test
        @DisplayName("request handler that does not mutate: chain receives the original request")
        void requestHandlerWithoutMutationDiscardsWrapper() throws Exception {
            val plan     = planFor(permitWith("noop"), noopRequestObserverProvider());
            val request  = requestFor(plan);
            val response = new MockHttpServletResponse();
            val chain    = new RecordingFilterChain((req, res) -> ((HttpServletResponse) res).setStatus(200));
            filter.doFilter(request, response, chain);
            assertThat(chain.request()).isSameAs(request);
        }
    }

    private static ConstraintHandlerProvider runnerProvider() {
        return (constraint, supportedSignals) -> {
            if (!ConstraintResponsibility.isResponsible(constraint, "audit")) {
                return List.of();
            }
            ConstraintHandler.Runner h = () -> {};
            return List.of(new ScopedConstraintHandler(h, Signal.DecisionSignal.SIGNAL_TYPE, 0));
        };
    }

    private static ConstraintHandlerProvider responseStampProvider() {
        return (constraint, supportedSignals) -> {
            if (!ConstraintResponsibility.isResponsible(constraint, "stamp")) {
                return List.of();
            }
            ConstraintHandler.Consumer<MutableHttpResponse> h = resp -> resp.setHeader("X-Trace", "abc");
            return List.of(new ScopedConstraintHandler(h, Signal.HttpResponseSignal.SIGNAL_TYPE, 0));
        };
    }

    private static ConstraintHandlerProvider noopRequestObserverProvider() {
        return (constraint, supportedSignals) -> {
            if (!ConstraintResponsibility.isResponsible(constraint, "noop")) {
                return List.of();
            }
            ConstraintHandler.Consumer<MutableHttpRequest> h = req -> { /* observe only, no mutation */ };
            return List.of(new ScopedConstraintHandler(h, Signal.HttpRequestMutationSignal.SIGNAL_TYPE, 0));
        };
    }

    @Nested
    @DisplayName("Request mutation")
    class RequestMutation {

        @Test
        @DisplayName("a header set by an obligation is visible to the downstream chain")
        void headerInjected() throws Exception {
            val plan     = planFor(permitWith("inject"), new ConstraintHandlerProvider() {
                             @Override
                             public List<ScopedConstraintHandler> getConstraintHandlers(Value constraint,
                                     Set<SignalType> supportedSignals) {
                                 if (!ConstraintResponsibility.isResponsible(constraint, "inject")) {
                                     return List.of();
                                 }
                                 ConstraintHandler.Consumer<MutableHttpRequest> h = req -> req.setHeader("X-Tenant",
                                         "krynn");
                                 return List.of(new ScopedConstraintHandler(h,
                                         Signal.HttpRequestMutationSignal.SIGNAL_TYPE, 0));
                             }
                         });
            val request  = requestFor(plan);
            val response = new MockHttpServletResponse();
            val seen     = new AtomicReference<String>();
            val chain    = new RecordingFilterChain(
                    (req, res) -> seen.set(((HttpServletRequest) req).getHeader("X-Tenant")));
            filter.doFilter(request, response, chain);
            assertThat(seen.get()).isEqualTo("krynn");
        }

        @Test
        @DisplayName("a failing request-mutation obligation throws AccessDeniedException")
        void failureThrows() {
            val plan     = planFor(permitWith("boom"), new ConstraintHandlerProvider() {
                             @Override
                             public List<ScopedConstraintHandler> getConstraintHandlers(Value constraint,
                                     Set<SignalType> supportedSignals) {
                                 if (!ConstraintResponsibility.isResponsible(constraint, "boom")) {
                                     return List.of();
                                 }
                                 ConstraintHandler.Consumer<MutableHttpRequest> h = req -> {
                                                  throw new IllegalStateException("nope");
                                              };
                                 return List.of(new ScopedConstraintHandler(h,
                                         Signal.HttpRequestMutationSignal.SIGNAL_TYPE, 0));
                             }
                         });
            val request  = requestFor(plan);
            val response = new MockHttpServletResponse();
            val chain    = new RecordingFilterChain((req, res) -> {});
            assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("Response signal")
    class Response {

        @Test
        @DisplayName("an obligation can rewrite the controller-produced body before commit")
        void rewriteBody() throws Exception {
            val plan     = planFor(permitWith("rewrite"), new ConstraintHandlerProvider() {
                             @Override
                             public List<ScopedConstraintHandler> getConstraintHandlers(Value constraint,
                                     Set<SignalType> supportedSignals) {
                                 if (!ConstraintResponsibility.isResponsible(constraint, "rewrite")) {
                                     return List.of();
                                 }
                                 ConstraintHandler.Consumer<MutableHttpResponse> h = resp -> resp.setBody("REWRITTEN");
                                 return List
                                         .of(new ScopedConstraintHandler(h, Signal.HttpResponseSignal.SIGNAL_TYPE, 0));
                             }
                         });
            val request  = requestFor(plan);
            val response = new MockHttpServletResponse();
            val chain    = new RecordingFilterChain((req, res) -> {
                             ((HttpServletResponse) res).setStatus(200);
                             res.getWriter().write("ORIGINAL");
                         });
            filter.doFilter(request, response, chain);
            assertThat(response.getContentAsString()).isEqualTo("REWRITTEN");
        }

        @Test
        @DisplayName("an obligation can add headers visible on the underlying response")
        void addHeader() throws Exception {
            val plan     = planFor(permitWith("stamp"), new ConstraintHandlerProvider() {
                             @Override
                             public List<ScopedConstraintHandler> getConstraintHandlers(Value constraint,
                                     Set<SignalType> supportedSignals) {
                                 if (!ConstraintResponsibility.isResponsible(constraint, "stamp")) {
                                     return List.of();
                                 }
                                 ConstraintHandler.Consumer<MutableHttpResponse> h = resp -> resp.setHeader("X-Trace",
                                         "abc");
                                 return List
                                         .of(new ScopedConstraintHandler(h, Signal.HttpResponseSignal.SIGNAL_TYPE, 0));
                             }
                         });
            val request  = requestFor(plan);
            val response = new MockHttpServletResponse();
            val chain    = new RecordingFilterChain((req, res) -> ((HttpServletResponse) res).setStatus(200));
            filter.doFilter(request, response, chain);
            assertThat(response.getHeader("X-Trace")).isEqualTo("abc");
        }

        @Test
        @DisplayName("a failing response obligation throws AccessDeniedException and does not commit")
        void failureThrows() throws Exception {
            val plan     = planFor(permitWith("boom"), new ConstraintHandlerProvider() {
                             @Override
                             public List<ScopedConstraintHandler> getConstraintHandlers(Value constraint,
                                     Set<SignalType> supportedSignals) {
                                 if (!ConstraintResponsibility.isResponsible(constraint, "boom")) {
                                     return List.of();
                                 }
                                 ConstraintHandler.Consumer<MutableHttpResponse> h = resp -> {
                                                  throw new IllegalStateException("nope");
                                              };
                                 return List
                                         .of(new ScopedConstraintHandler(h, Signal.HttpResponseSignal.SIGNAL_TYPE, 0));
                             }
                         });
            val request  = requestFor(plan);
            val response = new MockHttpServletResponse();
            val chain    = new RecordingFilterChain((req, res) -> ((HttpServletResponse) res).setStatus(200));
            assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                    .isInstanceOf(AccessDeniedException.class);
            assertThat(response.getContentAsString()).isEmpty();
        }

        @Test
        @DisplayName("an obligation observes the controller body via getBody before any rewrite")
        void observeBody() throws Exception {
            val observed = new AtomicReference<String>();
            val plan     = planFor(permitWith("observe"), new ConstraintHandlerProvider() {
                             @Override
                             public List<ScopedConstraintHandler> getConstraintHandlers(Value constraint,
                                     Set<SignalType> supportedSignals) {
                                 if (!ConstraintResponsibility.isResponsible(constraint, "observe")) {
                                     return List.of();
                                 }
                                 ConstraintHandler.Consumer<MutableHttpResponse> h = resp -> observed
                                         .set(resp.getBody());
                                 return List
                                         .of(new ScopedConstraintHandler(h, Signal.HttpResponseSignal.SIGNAL_TYPE, 0));
                             }
                         });
            val request  = requestFor(plan);
            val response = new MockHttpServletResponse();
            val chain    = new RecordingFilterChain((req, res) -> res.getWriter().write("from-controller"));
            filter.doFilter(request, response, chain);
            assertThat(observed.get()).isEqualTo("from-controller");
            assertThat(response.getContentAsString()).isEqualTo("from-controller");
        }
    }

    private static MockHttpServletRequest requestFor(EnforcementPlan plan) {
        val request = new MockHttpServletRequest("GET", "/r");
        request.setAttribute(HttpEnforcementContext.PLAN_ATTRIBUTE, plan);
        return request;
    }

    private static EnforcementPlan planFor(AuthorizationDecision decision, ConstraintHandlerProvider provider) {
        val planner = new EnforcementPlanner(List.of(provider), MAPPER);
        return planner.plan(decision, SUPPORTED_SIGNALS);
    }

    private static AuthorizationDecision permitWith(String obligationType) {
        val obligation = Value.ofObject(Map.of("type", Value.of(obligationType)));
        return new AuthorizationDecision(Decision.PERMIT, Value.ofArray(obligation), Value.EMPTY_ARRAY,
                Value.UNDEFINED);
    }

    @FunctionalInterface
    private interface ChainBody {
        void apply(ServletRequest request, ServletResponse response) throws Exception;
    }

    private static final class RecordingFilterChain extends MockFilterChain {
        private final ChainBody           body;
        private @Nullable ServletRequest  seenRequest;
        private @Nullable ServletResponse seenResponse;

        RecordingFilterChain(ChainBody body) {
            super(new NoopServlet(), new Filter[0]);
            this.body = body;
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) {
            this.seenRequest  = request;
            this.seenResponse = response;
            try {
                body.apply(request, response);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        ServletRequest request() {
            return seenRequest;
        }

        ServletResponse response() {
            return seenResponse;
        }
    }

    private static final class NoopServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;
    }
}
