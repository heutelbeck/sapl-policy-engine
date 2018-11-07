package io.sapl.pdp.embedded;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.functions.FunctionException;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.api.pdp.Request;
import io.sapl.api.pdp.Response;
import io.sapl.api.pip.AttributeException;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.combinators.DenyOverridesCombinator;
import io.sapl.interpreter.combinators.DenyUnlessPermitCombinator;
import io.sapl.interpreter.combinators.DocumentsCombinator;
import io.sapl.interpreter.combinators.OnlyOneApplicableCombinator;
import io.sapl.interpreter.combinators.PermitOverridesCombinator;
import io.sapl.interpreter.combinators.PermitUnlessDenyCombinator;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.api.prp.PolicyRetrievalPoint;
import io.sapl.prp.embedded.ResourcesPolicyRetrievalPoint;

public class EmbeddedPolicyDecisionPoint implements PolicyDecisionPoint {

	private static final String DEFAULT_SCAN_PACKAGE = "io.sapl";
	private static final SAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();
	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final String ALGORITHM_NOT_ALLOWED_FOR_PDP_LEVEL_COMBINATION = "algorithm not allowed for PDP level combination.";
	private static final String PDP_JSON = "/pdp.json";
	private static final Set<String> DEFAULT_LIBRARIES = new HashSet<>(Arrays.asList("filter", "standard"));
	private static final Set<String> DEFAULT_PIPS = new HashSet<>(Arrays.asList("http"));

	private PolicyRetrievalPoint prp;
	private DocumentsCombinator combinator;
	private Map<String, JsonNode> variables = new HashMap<>();
	private AttributeContext attributeCtx;
	private FunctionContext functionCtx;
	private EmbeddedPolicyDecisionPointConfiguration configuration;

	public EmbeddedPolicyDecisionPoint()
			throws IOException, PolicyEvaluationException, AttributeException, FunctionException {
		this(null);
	}

	public EmbeddedPolicyDecisionPoint(String policyPath)
			throws IOException, PolicyEvaluationException, AttributeException, FunctionException {
		this(policyPath, loadConfiguration(policyPath));
	}

	public EmbeddedPolicyDecisionPoint(String policyPath, EmbeddedPolicyDecisionPointConfiguration configuration)
			throws AttributeException, FunctionException, IOException, PolicyEvaluationException {
		this.configuration = configuration;
		functionCtx = new AnnotationFunctionContext();
		attributeCtx = new AnnotationAttributeContext();
		prp = ResourcesPolicyRetrievalPoint.of(policyPath, configuration, functionCtx);
		importAttributeFindersFromPackage(DEFAULT_SCAN_PACKAGE);
		importFunctionLibrariesFromPackage(DEFAULT_SCAN_PACKAGE);
		buildVariables(configuration);
		buildCombinator(configuration);
	}

	private static EmbeddedPolicyDecisionPointConfiguration loadConfiguration(String policyPath) {
		String path = policyPath == null ? ResourcesPolicyRetrievalPoint.DEFAULT_PATH : policyPath;
		PathMatchingResourcePatternResolver pm = new PathMatchingResourcePatternResolver();
		Resource configFile = pm.getResource(path + PDP_JSON);
		try {
			return MAPPER.readValue(configFile.getURL().openStream(), EmbeddedPolicyDecisionPointConfiguration.class);
		} catch (IOException e) {
			return new EmbeddedPolicyDecisionPointConfiguration();
		}
	}

	private static Set<BeanDefinition> discover(Class<? extends Annotation> clazz, String scanPackage) {
		ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter(new AnnotationTypeFilter(clazz));
		return scanner.findCandidateComponents(scanPackage);
	}

	public final void importAttributeFindersFromPackage(String scanPackage) throws AttributeException {
		for (BeanDefinition bd : discover(PolicyInformationPoint.class, scanPackage)) {
			try {
				Class<?> clazz = Class.forName(bd.getBeanClassName());
				String name = clazz.getAnnotation(PolicyInformationPoint.class).name();
				if (DEFAULT_PIPS.contains(name) || configuration.getAttributeFinders().contains(name)) {
					attributeCtx.loadPolicyInformationPoint(clazz.getConstructor().newInstance());
				}
			} catch (NoSuchMethodException | SecurityException | ClassNotFoundException | InstantiationException
					| IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				throw new AttributeException(e);
			}
		}
	}

	public final void importFunctionLibrariesFromPackage(String scanPackage) throws FunctionException {
		for (BeanDefinition bd : discover(FunctionLibrary.class, scanPackage)) {
			try {
				Class<?> clazz = Class.forName(bd.getBeanClassName());
				String name = clazz.getAnnotation(FunctionLibrary.class).name();
				if (DEFAULT_LIBRARIES.contains(name) || configuration.getLibraries().contains(name)) {
					functionCtx.loadLibrary(clazz.getConstructor().newInstance());
				}
			} catch (NoSuchMethodException | SecurityException | ClassNotFoundException | InstantiationException
					| IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				throw new FunctionException(e);
			}
		}
	}

	private final void buildVariables(EmbeddedPolicyDecisionPointConfiguration configuration) {
		for (Entry<String, JsonNode> var : configuration.getVariables().entrySet()) {
			variables.put(var.getKey(), var.getValue());
		}
	}

	private final void buildCombinator(EmbeddedPolicyDecisionPointConfiguration configuration) {
		switch (configuration.getAlgorithm()) {
		case PERMIT_UNLESS_DENY:
			combinator = new PermitUnlessDenyCombinator(INTERPRETER);
			break;
		case DENY_UNLESS_PERMIT:
			combinator = new DenyUnlessPermitCombinator(INTERPRETER);
			break;
		case PERMIT_OVERRIDES:
			combinator = new PermitOverridesCombinator(INTERPRETER);
			break;
		case DENY_OVERRIDES:
			combinator = new DenyOverridesCombinator(INTERPRETER);
			break;
		case ONLY_ONE_APPLICABLE:
			combinator = new OnlyOneApplicableCombinator(INTERPRETER);
			break;
		default:
			throw new IllegalArgumentException(ALGORITHM_NOT_ALLOWED_FOR_PDP_LEVEL_COMBINATION);
		}
	}

	@Override
	public Response decide(Request request) {
		PolicyRetrievalResult retrievalResult = prp.retrievePolicies(request, functionCtx, variables);
		return combinator.combineMatchingDocuments(retrievalResult.getMatchingDocuments(),
				retrievalResult.isErrorsInTarget(), request, attributeCtx, functionCtx, variables);
	}

	@Override
	public Response decide(Object subject, Object action, Object resource, Object environment) {
		Request request = new Request(MAPPER.convertValue(subject, JsonNode.class),
				MAPPER.convertValue(action, JsonNode.class), MAPPER.convertValue(resource, JsonNode.class),
				MAPPER.convertValue(environment, JsonNode.class));
		return decide(request);
	}

	@Override
	public Response decide(Object subject, Object action, Object resource) {
		return decide(subject, action, resource, null);
	}

}
