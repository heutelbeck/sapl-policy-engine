/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.pdp;

import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.functions.FunctionLibrarySupplier;
import io.sapl.api.functions.StaticFunctionLibrarySupplier;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.AuthorizationSubscriptionInterceptor;
import io.sapl.api.pdp.TracedDecision;
import io.sapl.api.pdp.TracedDecisionInterceptor;
import io.sapl.api.pip.PolicyInformationPointSupplier;
import io.sapl.api.pip.StaticPolicyInformationPointSupplier;
import io.sapl.attributes.broker.api.AttributeStreamBroker;
import io.sapl.attributes.broker.impl.AnnotationPolicyInformationPointLoader;
import io.sapl.attributes.broker.impl.CachingAttributeStreamBroker;
import io.sapl.attributes.broker.impl.InMemoryPolicyInformationPointDocumentationProvider;
import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.functions.LoggingFunctionLibrary;
import io.sapl.functions.SchemaValidationLibrary;
import io.sapl.functions.StandardFunctionLibrary;
import io.sapl.functions.TemporalFunctionLibrary;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.pdp.config.PDPConfiguration;
import io.sapl.pdp.config.PDPConfigurationProvider;
import io.sapl.pdp.config.VariablesAndCombinatorSource;
import io.sapl.pdp.config.filesystem.FileSystemVariablesAndCombinatorSource;
import io.sapl.pdp.config.fixed.FixedFunctionsAndAttributesPDPConfigurationProvider;
import io.sapl.pdp.config.resources.ResourcesVariablesAndCombinatorSource;
import io.sapl.pip.http.HttpPolicyInformationPoint;
import io.sapl.pip.http.ReactiveWebClient;
import io.sapl.pip.time.TimePolicyInformationPoint;
import io.sapl.prp.Document;
import io.sapl.prp.GenericInMemoryIndexedPolicyRetrievalPointSource;
import io.sapl.prp.PolicyRetrievalPointSource;
import io.sapl.prp.filesystem.FileSystemPrpUpdateEventSource;
import io.sapl.prp.index.UpdateEventDrivenPolicyRetrievalPoint;
import io.sapl.prp.index.naive.NaiveImmutableParsedDocumentIndex;
import io.sapl.prp.resources.ResourcesPrpUpdateEventSource;
import io.sapl.validation.ValidatorFactory;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;

@UtilityClass
public class PolicyDecisionPointFactory {

    private static final String DEFAULT_FILE_LOCATION      = "~/sapl/policies";
    private static final String DEFAULT_RESOURCES_LOCATION = "/policies";

    public static EmbeddedPolicyDecisionPoint filesystemPolicyDecisionPoint() throws InitializationException {
        return filesystemPolicyDecisionPoint(DEFAULT_FILE_LOCATION);
    }

    public static EmbeddedPolicyDecisionPoint filesystemPolicyDecisionPoint(String path)
            throws InitializationException {
        return filesystemPolicyDecisionPoint(path, List::of, List::of, List::of, List::of);
    }

    public static EmbeddedPolicyDecisionPoint fixedInRamPolicyDecisionPoint(Collection<String> documents,
            PolicyDocumentCombiningAlgorithm documentsCombinator, Map<String, Val> variables)
            throws InitializationException {
        final var functionContext       = constructFunctionContext(List::of, List::of);
        final var attributeStreamBroker = constructAttributeStreamBroker(List::of, List::of);
        return fixedInRamPolicyDecisionPoint(new DefaultSAPLInterpreter(), documents, documentsCombinator, variables,
                attributeStreamBroker, functionContext, UnaryOperator.identity(), UnaryOperator.identity());
    }

    public static EmbeddedPolicyDecisionPoint fixedInRamPolicyDecisionPoint(SAPLInterpreter parser,
            Collection<String> documents, PolicyDocumentCombiningAlgorithm documentsCombinator,
            Map<String, Val> variables, FunctionLibrarySupplier functionLibraries,
            StaticFunctionLibrarySupplier staticFunctionLibraries, PolicyInformationPointSupplier pips,
            StaticPolicyInformationPointSupplier staticPips, UnaryOperator<TracedDecision> decisionInterceptorChain,
            UnaryOperator<AuthorizationSubscription> subscriptionInterceptorChain) throws InitializationException {
        final var functionContext       = constructFunctionContext(functionLibraries, staticFunctionLibraries);
        final var attributeStreamBroker = constructAttributeStreamBroker(pips, staticPips);
        return fixedInRamPolicyDecisionPoint(parser, documents, documentsCombinator, variables, attributeStreamBroker,
                functionContext, decisionInterceptorChain, subscriptionInterceptorChain);
    }

    public static EmbeddedPolicyDecisionPoint fixedInRamPolicyDecisionPoint(SAPLInterpreter parser,
            Collection<String> documents, PolicyDocumentCombiningAlgorithm documentsCombinator,
            Map<String, Val> variables, AttributeStreamBroker attributeStreamBroker, FunctionContext functionContext,
            UnaryOperator<TracedDecision> decisionInterceptorChain,
            UnaryOperator<AuthorizationSubscription> subscriptionInterceptorChain) {
        final var documentsById = new HashMap<String, Document>();

        var consistent = true;
        for (var source : documents) {
            final var document = parser.parseDocument(source);
            if (document.isInvalid()) {
                consistent = false;
                documentsById.put(UUID.randomUUID().toString(), parser.parseDocument(source));
            } else {
                final var original = documentsById.put(document.name(), document);
                if (original != null) {
                    consistent = false;
                }
            }
        }
        final var policyRetrievalPoint = new NaiveImmutableParsedDocumentIndex(documentsById, consistent);

        final var pdpConfiguration = new PDPConfiguration(UUID.randomUUID().toString(), attributeStreamBroker,
                functionContext, variables, documentsCombinator, decisionInterceptorChain, subscriptionInterceptorChain,
                policyRetrievalPoint);

        return new EmbeddedPolicyDecisionPoint(() -> Flux.just(pdpConfiguration));
    }

    public static EmbeddedPolicyDecisionPoint filesystemPolicyDecisionPoint(PolicyInformationPointSupplier pips,
            StaticPolicyInformationPointSupplier staticPips, FunctionLibrarySupplier functionLibraries,
            StaticFunctionLibrarySupplier staticFunctionLibraries) throws InitializationException {
        return filesystemPolicyDecisionPoint(DEFAULT_FILE_LOCATION, pips, staticPips, functionLibraries,
                staticFunctionLibraries);
    }

    public static EmbeddedPolicyDecisionPoint filesystemPolicyDecisionPoint(String path,
            PolicyInformationPointSupplier pips, StaticPolicyInformationPointSupplier staticPips,
            FunctionLibrarySupplier functionLibraries, StaticFunctionLibrarySupplier staticFunctionLibraries)
            throws InitializationException {
        return filesystemPolicyDecisionPoint(path, pips, staticPips, functionLibraries, staticFunctionLibraries,
                List.of(), List.of());
    }

    public static EmbeddedPolicyDecisionPoint filesystemPolicyDecisionPoint(String path,
            PolicyInformationPointSupplier pips, StaticPolicyInformationPointSupplier staticPips,
            FunctionLibrarySupplier functionLibraries, StaticFunctionLibrarySupplier staticFunctionLibraries,
            Collection<AuthorizationSubscriptionInterceptor> subscriptionInterceptors,
            Collection<TracedDecisionInterceptor> authorizationSubscriptionInterceptors)
            throws InitializationException {
        final var fileSource            = new FileSystemVariablesAndCombinatorSource(path);
        final var configurationProvider = constructFilesystemConfigurationProvider(path, fileSource, pips, staticPips,
                functionLibraries, staticFunctionLibraries, subscriptionInterceptors,
                authorizationSubscriptionInterceptors);
        return new EmbeddedPolicyDecisionPoint(configurationProvider);
    }

    public static EmbeddedPolicyDecisionPoint resourcesPolicyDecisionPoint() throws InitializationException {
        return resourcesPolicyDecisionPoint(DEFAULT_RESOURCES_LOCATION);
    }

    public static EmbeddedPolicyDecisionPoint resourcesPolicyDecisionPoint(PolicyInformationPointSupplier pips,
            StaticPolicyInformationPointSupplier staticPips, FunctionLibrarySupplier functionLibraries,
            StaticFunctionLibrarySupplier staticFunctionLibraries) throws InitializationException {
        return resourcesPolicyDecisionPoint(DEFAULT_RESOURCES_LOCATION, pips, staticPips, functionLibraries,
                staticFunctionLibraries, List.of(), List.of());
    }

    public static EmbeddedPolicyDecisionPoint resourcesPolicyDecisionPoint(String path) throws InitializationException {
        return resourcesPolicyDecisionPoint(path, List::of, List::of, List::of, List::of, List.of(), List.of());
    }

    public static EmbeddedPolicyDecisionPoint resourcesPolicyDecisionPoint(String path,
            PolicyInformationPointSupplier pips, StaticPolicyInformationPointSupplier staticPips,
            FunctionLibrarySupplier functionLibraries, StaticFunctionLibrarySupplier staticFunctionLibraries,
            Collection<AuthorizationSubscriptionInterceptor> subscriptionInterceptors,
            Collection<TracedDecisionInterceptor> authorizationSubscriptionInterceptors)
            throws InitializationException {
        final var resourcesSource       = new ResourcesVariablesAndCombinatorSource(path, new ObjectMapper());
        final var configurationProvider = constructResourcesConfigurationProvider(path, resourcesSource, pips,
                staticPips, functionLibraries, staticFunctionLibraries, subscriptionInterceptors,
                authorizationSubscriptionInterceptors);
        return new EmbeddedPolicyDecisionPoint(configurationProvider);
    }

    private static PDPConfigurationProvider constructResourcesConfigurationProvider(String path,
            VariablesAndCombinatorSource combinatorProvider, PolicyInformationPointSupplier pips,
            StaticPolicyInformationPointSupplier staticPips, FunctionLibrarySupplier functionLibraries,
            StaticFunctionLibrarySupplier staticFunctionLibraries,
            Collection<AuthorizationSubscriptionInterceptor> subscriptionInterceptors,
            Collection<TracedDecisionInterceptor> authorizationSubscriptionInterceptors)
            throws InitializationException {
        final var                  functionCtx = constructFunctionContext(functionLibraries, staticFunctionLibraries);
        final var                  broker      = constructAttributeStreamBroker(pips, staticPips);
        PolicyRetrievalPointSource prpSource   = constructResourcesPolicyRetrievalPointSource(path);
        return new FixedFunctionsAndAttributesPDPConfigurationProvider(broker, functionCtx, combinatorProvider,
                subscriptionInterceptors, authorizationSubscriptionInterceptors, prpSource);
    }

    private static PDPConfigurationProvider constructFilesystemConfigurationProvider(String path,
            VariablesAndCombinatorSource combinatorProvider, PolicyInformationPointSupplier pips,
            StaticPolicyInformationPointSupplier staticPips, FunctionLibrarySupplier functionLibraries,
            StaticFunctionLibrarySupplier staticFunctionLibraries,
            Collection<AuthorizationSubscriptionInterceptor> subscriptionInterceptors,
            Collection<TracedDecisionInterceptor> authorizationSubscriptionInterceptors)
            throws InitializationException {
        final var                  functionCtx = constructFunctionContext(functionLibraries, staticFunctionLibraries);
        final var                  broker      = constructAttributeStreamBroker(pips, staticPips);
        PolicyRetrievalPointSource prpSource   = constructFilesystemPolicyRetrievalPointSource(path);
        return new FixedFunctionsAndAttributesPDPConfigurationProvider(broker, functionCtx, combinatorProvider,
                subscriptionInterceptors, authorizationSubscriptionInterceptors, prpSource);
    }

    private static FunctionContext constructFunctionContext(FunctionLibrarySupplier functionLibraries,
            StaticFunctionLibrarySupplier staticFunctionLibraries) throws InitializationException {
        final var functionCtx = new AnnotationFunctionContext(functionLibraries, staticFunctionLibraries);
        functionCtx.loadLibrary(FilterFunctionLibrary.class);
        functionCtx.loadLibrary(StandardFunctionLibrary.class);
        functionCtx.loadLibrary(TemporalFunctionLibrary.class);
        functionCtx.loadLibrary(SchemaValidationLibrary.class);
        functionCtx.loadLibrary(LoggingFunctionLibrary.class);
        return functionCtx;
    }

    private static AttributeStreamBroker constructAttributeStreamBroker(PolicyInformationPointSupplier pips,
            StaticPolicyInformationPointSupplier staticPips) {
        final var mapper                = new ObjectMapper();
        final var attributeStreamBroker = new CachingAttributeStreamBroker();
        final var docsProvider          = new InMemoryPolicyInformationPointDocumentationProvider();
        final var loader                = new AnnotationPolicyInformationPointLoader(attributeStreamBroker,
                docsProvider, new ValidatorFactory(mapper));
        loader.loadPolicyInformationPoint(new TimePolicyInformationPoint(Clock.systemUTC()));
        loader.loadPolicyInformationPoint(new HttpPolicyInformationPoint(new ReactiveWebClient(mapper)));
        loader.loadPolicyInformationPoints(pips);
        loader.loadPolicyInformationPoints(staticPips);
        return attributeStreamBroker;
    }

    private static PolicyRetrievalPointSource constructResourcesPolicyRetrievalPointSource(String resourcePath) {
        final var seedIndex = constructDocumentIndex();
        final var source    = new ResourcesPrpUpdateEventSource(resourcePath, new DefaultSAPLInterpreter());
        return new GenericInMemoryIndexedPolicyRetrievalPointSource(seedIndex, source);
    }

    private static PolicyRetrievalPointSource constructFilesystemPolicyRetrievalPointSource(String policiesFolder) {
        final var seedIndex = constructDocumentIndex();
        final var source    = new FileSystemPrpUpdateEventSource(policiesFolder, new DefaultSAPLInterpreter());
        return new GenericInMemoryIndexedPolicyRetrievalPointSource(seedIndex, source);
    }

    private static UpdateEventDrivenPolicyRetrievalPoint constructDocumentIndex() {
        return new NaiveImmutableParsedDocumentIndex();
    }

}
