/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import java.util.ArrayList;
import java.util.Collection;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.functions.StandardFunctionLibrary;
import io.sapl.functions.TemporalFunctionLibrary;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.pdp.config.FixedFunctionsAndAttributesPDPConfigurationProvider;
import io.sapl.pdp.config.PDPConfigurationProvider;
import io.sapl.pdp.config.VariablesAndCombinatorSource;
import io.sapl.pdp.config.filesystem.FileSystemVariablesAndCombinatorSource;
import io.sapl.pdp.config.resources.ResourcesVariablesAndCombinatorSource;
import io.sapl.pip.TimePolicyInformationPoint;
import io.sapl.prp.GenericInMemoryIndexedPolicyRetrievalPoint;
import io.sapl.prp.PolicyRetrievalPoint;
import io.sapl.prp.filesystem.FileSystemPrpUpdateEventSource;
import io.sapl.prp.index.ImmutableParsedDocumentIndex;
import io.sapl.prp.index.naive.NaiveImmutableParsedDocumentIndex;
import io.sapl.prp.resources.ResourcesPrpUpdateEventSource;
import lombok.experimental.UtilityClass;

@UtilityClass
public class PolicyDecisionPointFactory {

	private static final String DEFAULT_FILE_LOCATION = "~/sapl/policies";

	private static final String DEFAULT_RESOURCES_LOCATION = "/policies";

	public static EmbeddedPolicyDecisionPoint filesystemPolicyDecisionPoint() throws InitializationException {
		return filesystemPolicyDecisionPoint(DEFAULT_FILE_LOCATION);
	}

	public static EmbeddedPolicyDecisionPoint filesystemPolicyDecisionPoint(String path)
			throws InitializationException {
		return filesystemPolicyDecisionPoint(path, new ArrayList<>(1), new ArrayList<>(1));
	}

	public static EmbeddedPolicyDecisionPoint filesystemPolicyDecisionPoint(Collection<Object> policyInformationPoints,
			Collection<Object> functionLibraries) throws InitializationException {
		return filesystemPolicyDecisionPoint(DEFAULT_FILE_LOCATION, policyInformationPoints, functionLibraries);
	}

	public static EmbeddedPolicyDecisionPoint filesystemPolicyDecisionPoint(String path,
			Collection<Object> policyInformationPoints, Collection<Object> functionLibraries)
			throws InitializationException {
		var fileSource = new FileSystemVariablesAndCombinatorSource(path);
		var configurationProvider = constructConfigurationProvider(fileSource, policyInformationPoints,
				functionLibraries);
		var policyRetrievalPoint = constructFilesystemPolicyRetrievalPoint(path);
		return new EmbeddedPolicyDecisionPoint(configurationProvider, policyRetrievalPoint);
	}

	public static EmbeddedPolicyDecisionPoint resourcesPolicyDecisionPoint() throws InitializationException {
		return resourcesPolicyDecisionPoint(DEFAULT_RESOURCES_LOCATION);
	}

	public static EmbeddedPolicyDecisionPoint resourcesPolicyDecisionPoint(Collection<Object> policyInformationPoints,
			Collection<Object> functionLibraries) throws InitializationException {
		return resourcesPolicyDecisionPoint(DEFAULT_RESOURCES_LOCATION, policyInformationPoints, functionLibraries);
	}

	public static EmbeddedPolicyDecisionPoint resourcesPolicyDecisionPoint(String path) throws InitializationException {
		return resourcesPolicyDecisionPoint(path, new ArrayList<>(1), new ArrayList<>(1));
	}

	public static EmbeddedPolicyDecisionPoint resourcesPolicyDecisionPoint(String path,
			Collection<Object> policyInformationPoints, Collection<Object> functionLibraries)
			throws InitializationException {
		ResourcesVariablesAndCombinatorSource resourcesSource;
		resourcesSource = new ResourcesVariablesAndCombinatorSource(EmbeddedPolicyDecisionPoint.class, path,
				new ObjectMapper());
		var configurationProvider = constructConfigurationProvider(resourcesSource, policyInformationPoints,
				functionLibraries);
		var policyRetrievalPoint = constructResourcesPolicyRetrievalPoint(path);
		return new EmbeddedPolicyDecisionPoint(configurationProvider, policyRetrievalPoint);
	}

	private static PDPConfigurationProvider constructConfigurationProvider(
			VariablesAndCombinatorSource combinatorProvider, Collection<Object> policyInformationPoints,
			Collection<Object> functionLibraries) throws InitializationException {
		var functionCtx = constructFunctionContext(functionLibraries);
		var attributeCtx = constructAttributeContext(policyInformationPoints);
		return new FixedFunctionsAndAttributesPDPConfigurationProvider(attributeCtx, functionCtx, combinatorProvider);
	}

	private static FunctionContext constructFunctionContext(Collection<Object> functionLibraries)
			throws InitializationException {
		var functionCtx = new AnnotationFunctionContext();
		functionCtx.loadLibrary(new FilterFunctionLibrary());
		functionCtx.loadLibrary(new StandardFunctionLibrary());
		functionCtx.loadLibrary(new TemporalFunctionLibrary());
		for (var library : functionLibraries)
			functionCtx.loadLibrary(library);
		return functionCtx;
	}

	private static AttributeContext constructAttributeContext(Collection<Object> policyInformationPoints)
			throws InitializationException {
		var attributeCtx = new AnnotationAttributeContext();
		attributeCtx.loadPolicyInformationPoint(new TimePolicyInformationPoint(Clock.systemUTC()));
		for (var pip : policyInformationPoints)
			attributeCtx.loadPolicyInformationPoint(pip);
		return attributeCtx;
	}

	private static PolicyRetrievalPoint constructResourcesPolicyRetrievalPoint(String resourcePath)
			throws InitializationException {
		var seedIndex = constructDocumentIndex();
		var source = new ResourcesPrpUpdateEventSource(resourcePath, new DefaultSAPLInterpreter());

		return new GenericInMemoryIndexedPolicyRetrievalPoint(seedIndex, source);
	}

	private static PolicyRetrievalPoint constructFilesystemPolicyRetrievalPoint(String policiesFolder) {
		var seedIndex = constructDocumentIndex();
		var source = new FileSystemPrpUpdateEventSource(policiesFolder, new DefaultSAPLInterpreter());
		return new GenericInMemoryIndexedPolicyRetrievalPoint(seedIndex, source);
	}

	private static ImmutableParsedDocumentIndex constructDocumentIndex() {
		return new NaiveImmutableParsedDocumentIndex();
	}

}
