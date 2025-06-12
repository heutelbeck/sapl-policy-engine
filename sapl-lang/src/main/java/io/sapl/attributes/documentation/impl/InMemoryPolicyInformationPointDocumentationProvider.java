package io.sapl.attributes.documentation.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.attributes.broker.api.AttributeBrokerException;
import io.sapl.attributes.broker.api.AttributeFinderSpecification;
import io.sapl.attributes.broker.api.PolicyInformationPointSpecification;
import io.sapl.attributes.documentation.api.PolicyInformationPointDocumentationProvider;
import io.sapl.interpreter.pip.PolicyInformationPointDocumentation;

public class InMemoryPolicyInformationPointDocumentationProvider
        implements PolicyInformationPointDocumentationProvider {

    private final Map<String, PolicyInformationPointSpecification> pipRegistry        = new HashMap<>();
    private final Map<String, PolicyInformationPointDocumentation> documentationCache = new HashMap<>();

    private final Object lock = new Object();

    @Override
    public void loadPolicyInformationPoint(PolicyInformationPointSpecification pipSpecification) {
        synchronized (lock) {
            final var pipName = pipSpecification.name();
            if (pipRegistry.containsKey(pipName)) {
                throw new AttributeBrokerException(
                        String.format("Cannot load documentation for %s. Name already in use.", pipSpecification));
            }
            pipRegistry.put(pipName, pipSpecification);
            documentationCache.put(pipName, toDocumentation(pipSpecification));
        }
    }

    @Override
    public void unloadPolicyInformationPoint(String name) {
        synchronized (lock) {
            pipRegistry.remove(name);
            documentationCache.remove(name);
        }
    }

    @Override
    public List<String> providedFunctionsOfLibrary(String libraryName) {
        synchronized (lock) {
            final var library = pipRegistry.get(libraryName);
            if (null == library) {
                return List.of();
            }
            return library.attributeFinders().stream().map(AttributeFinderSpecification::attributeName)
                    .map(n -> n.substring(libraryName.length() + 1)).toList();
        }
    }

    @Override
    public boolean isProvidedFunction(String fullyQualifiedFunctionName) {
        synchronized (lock) {
            for (var pip : pipRegistry.values()) {
                for (var finder : pip.attributeFinders()) {
                    if (finder.attributeName().equals(fullyQualifiedFunctionName)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    @Override
    public List<String> getAllFullyQualifiedFunctions() {
        synchronized (lock) {
            return pipRegistry.values().stream().flatMap(spec -> spec.attributeFinders().stream())
                    .map(AttributeFinderSpecification::attributeName).toList();
        }
    }

    @Override
    public Map<String, JsonNode> getAttributeSchemas() {
        synchronized (lock) {
            final var result = new HashMap<String, JsonNode>();
            for (var pip : pipRegistry.values()) {
                for (var finder : pip.attributeFinders()) {
                    result.put(finder.attributeName(), finder.attributeSchema());
                }
            }
            return result;
        }
    }

    @Override
    public List<AttributeFinderSpecification> getAttributeMetatata() {
        synchronized (lock) {
            final var result = new ArrayList<AttributeFinderSpecification>();
            for (var pip : pipRegistry.values()) {
                result.addAll(pip.attributeFinders());
            }
            return result;
        }
    }

    @Override
    public List<String> getAvailableLibraries() {
        synchronized (lock) {
            return pipRegistry.keySet().stream().toList();
        }
    }

    @Override
    public List<String> getEnvironmentAttributeCodeTemplates() {
        synchronized (lock) {
            final var result = new ArrayList<String>();
            for (var pip : pipRegistry.values()) {
                pip.attributeFinders().stream().filter(AttributeFinderSpecification::isEnvironmentAttribute)
                        .map(AttributeFinderSpecification::codeTemplate).forEach(result::add);
            }
            return result;
        }
    }

    @Override
    public List<String> getAttributeCodeTemplates() {
        synchronized (lock) {
            final var result = new ArrayList<String>();
            for (var pip : pipRegistry.values()) {
                pip.attributeFinders().stream().filter(f -> !f.isEnvironmentAttribute())
                        .map(AttributeFinderSpecification::codeTemplate).forEach(result::add);
            }
            return result;
        }
    }

    @Override
    public Map<String, String> getDocumentedAttributeCodeTemplates() {
        synchronized (lock) {
            final var result = new HashMap<String, String>();
            for (var pip : pipRegistry.values()) {
                for (var finder : pip.attributeFinders()) {
                    result.put(finder.codeTemplate(), finder.documentation());
                }
            }
            return result;
        }
    }

    @Override
    public List<PolicyInformationPointDocumentation> getDocumentation() {
        synchronized (lock) {
            return documentationCache.values().stream().toList();
        }
    }

    private PolicyInformationPointDocumentation toDocumentation(PolicyInformationPointSpecification specification) {
        final var finderDocs = new HashMap<String, String>();
        for (var finder : specification.attributeFinders()) {
            finderDocs.put(finder.codeTemplate(), finder.documentation());
        }
        final var documentation = new PolicyInformationPointDocumentation(specification.name(),
                specification.description(), specification.documentation());
        documentation.setDocumentation(finderDocs);
        return documentation;
    }

}
