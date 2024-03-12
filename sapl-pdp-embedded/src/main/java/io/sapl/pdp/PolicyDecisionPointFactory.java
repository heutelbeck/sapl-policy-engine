/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.functions.FunctionLibrarySupplier;
import io.sapl.api.functions.StaticFunctionLibrarySupplier;
import io.sapl.api.pdp.AuthorizationSubscriptionInterceptor;
import io.sapl.api.pdp.TracedDecisionInterceptor;
import io.sapl.api.pip.PolicyInformationPointSupplier;
import io.sapl.api.pip.StaticPolicyInformationPointSupplier;
import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.functions.LoggingFunctionLibrary;
import io.sapl.functions.SchemaValidationLibrary;
import io.sapl.functions.StandardFunctionLibrary;
import io.sapl.functions.TemporalFunctionLibrary;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.pdp.config.PDPConfigurationProvider;
import io.sapl.pdp.config.VariablesAndCombinatorSource;
import io.sapl.pdp.config.filesystem.FileSystemVariablesAndCombinatorSource;
import io.sapl.pdp.config.fixed.FixedFunctionsAndAttributesPDPConfigurationProvider;
import io.sapl.pdp.config.resources.ResourcesVariablesAndCombinatorSource;
import io.sapl.pip.TimePolicyInformationPoint;
import io.sapl.pip.http.HttpPolicyInformationPoint;
import io.sapl.pip.http.ReactiveWebClient;
import io.sapl.prp.GenericInMemoryIndexedPolicyRetrievalPointSource;
import io.sapl.prp.PolicyRetrievalPointSource;
import io.sapl.prp.filesystem.FileSystemPrpUpdateEventSource;
import io.sapl.prp.index.UpdateEventDrivenPolicyRetrievalPoint;
import io.sapl.prp.index.naive.NaiveImmutableParsedDocumentIndex;
import io.sapl.prp.resources.ResourcesPrpUpdateEventSource;
import lombok.experimental.UtilityClass;

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
        var fileSource            = new FileSystemVariablesAndCombinatorSource(path);
        var configurationProvider = constructFilesystemConfigurationProvider(path, fileSource, pips, staticPips,
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
        var resourcesSource       = new ResourcesVariablesAndCombinatorSource(path, new ObjectMapper());
        var configurationProvider = constructResourcesConfigurationProvider(path, resourcesSource, pips, staticPips,
                functionLibraries, staticFunctionLibraries, subscriptionInterceptors,
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
        var                        functionCtx  = constructFunctionContext(functionLibraries, staticFunctionLibraries);
        var                        attributeCtx = constructAttributeContext(pips, staticPips);
        PolicyRetrievalPointSource prpSource    = constructResourcesPolicyRetrievalPointSource(path);
        return new FixedFunctionsAndAttributesPDPConfigurationProvider(attributeCtx, functionCtx, combinatorProvider,
                subscriptionInterceptors, authorizationSubscriptionInterceptors, prpSource);
    }

    private static PDPConfigurationProvider constructFilesystemConfigurationProvider(String path,
            VariablesAndCombinatorSource combinatorProvider, PolicyInformationPointSupplier pips,
            StaticPolicyInformationPointSupplier staticPips, FunctionLibrarySupplier functionLibraries,
            StaticFunctionLibrarySupplier staticFunctionLibraries,
            Collection<AuthorizationSubscriptionInterceptor> subscriptionInterceptors,
            Collection<TracedDecisionInterceptor> authorizationSubscriptionInterceptors)
            throws InitializationException {
        var                        functionCtx  = constructFunctionContext(functionLibraries, staticFunctionLibraries);
        var                        attributeCtx = constructAttributeContext(pips, staticPips);
        PolicyRetrievalPointSource prpSource    = constructFilesystemPolicyRetrievalPointSource(path);
        return new FixedFunctionsAndAttributesPDPConfigurationProvider(attributeCtx, functionCtx, combinatorProvider,
                subscriptionInterceptors, authorizationSubscriptionInterceptors, prpSource);
    }

    private static FunctionContext constructFunctionContext(FunctionLibrarySupplier functionLibraries,
            StaticFunctionLibrarySupplier staticFunctionLibraries) throws InitializationException {
        var functionCtx = new AnnotationFunctionContext(functionLibraries, staticFunctionLibraries);
        functionCtx.loadLibrary(FilterFunctionLibrary.class);
        functionCtx.loadLibrary(StandardFunctionLibrary.class);
        functionCtx.loadLibrary(TemporalFunctionLibrary.class);
        functionCtx.loadLibrary(SchemaValidationLibrary.class);
        functionCtx.loadLibrary(LoggingFunctionLibrary.class);
        return functionCtx;
    }

    private static AttributeContext constructAttributeContext(PolicyInformationPointSupplier pips,
            StaticPolicyInformationPointSupplier staticPips) throws InitializationException {
        var attributeCtx = new AnnotationAttributeContext();
        attributeCtx.loadPolicyInformationPoint(new TimePolicyInformationPoint(Clock.systemUTC()));
        attributeCtx
                .loadPolicyInformationPoint(new HttpPolicyInformationPoint(new ReactiveWebClient(new ObjectMapper())));
        attributeCtx.loadPolicyInformationPoints(pips);
        attributeCtx.loadPolicyInformationPoints(staticPips);
        return attributeCtx;
    }

    private static PolicyRetrievalPointSource constructResourcesPolicyRetrievalPointSource(String resourcePath) {
        var seedIndex = constructDocumentIndex();
        var source    = new ResourcesPrpUpdateEventSource(resourcePath, new DefaultSAPLInterpreter());
        return new GenericInMemoryIndexedPolicyRetrievalPointSource(seedIndex, source);
    }

    private static PolicyRetrievalPointSource constructFilesystemPolicyRetrievalPointSource(String policiesFolder) {
        var seedIndex = constructDocumentIndex();
        var source    = new FileSystemPrpUpdateEventSource(policiesFolder, new DefaultSAPLInterpreter());
        return new GenericInMemoryIndexedPolicyRetrievalPointSource(seedIndex, source);
    }

    private static UpdateEventDrivenPolicyRetrievalPoint constructDocumentIndex() {
        return new NaiveImmutableParsedDocumentIndex();
    }

}
