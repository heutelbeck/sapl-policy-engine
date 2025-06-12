package io.sapl.attributes.documentation.api;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.attributes.broker.api.AttributeFinderSpecification;
import io.sapl.attributes.broker.api.PolicyInformationPointSpecification;
import io.sapl.interpreter.pip.PolicyInformationPointDocumentation;

public interface PolicyInformationPointDocumentationProvider {

    void loadPolicyInformationPoint(PolicyInformationPointSpecification pipSpecification);

    void unloadPolicyInformationPoint(String name);

    List<String> providedFunctionsOfLibrary(String library);

    boolean isProvidedFunction(String fullyQualifiedFunctionName);

    List<String> getAllFullyQualifiedFunctions();

    Map<String, JsonNode> getAttributeSchemas();

    List<AttributeFinderSpecification> getAttributeMetatata();

    List<String> getAvailableLibraries();

    List<String> getEnvironmentAttributeCodeTemplates();

    List<String> getAttributeCodeTemplates();

    Map<String, String> getDocumentedAttributeCodeTemplates();

    List<PolicyInformationPointDocumentation> getDocumentation();
}
