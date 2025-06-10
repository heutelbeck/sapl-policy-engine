package io.sapl.attributes.broker.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.attributes.broker.api.AttributeBrokerException;
import io.sapl.attributes.broker.api.AttributeFinderSpecification;
import io.sapl.attributes.broker.api.PolicyInformationPointDocumentationProvider;
import io.sapl.attributes.broker.api.PolicyInformationPointSpecification;
import io.sapl.interpreter.pip.PolicyInformationPointDocumentation;

public class InMemoryPolicyInformationPointDocumentationProvider
        implements PolicyInformationPointDocumentationProvider {

    private final Map<String, PolicyInformationPointSpecification> pipRegistry = new HashMap<>();

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
        }
    }

    @Override
    public void unloadPolicyInformationPoint(String name) {
        synchronized (lock) {
            pipRegistry.remove(name);
        }
    }

    @Override
    public List<String> providedFunctionsOfLibrary(String libraryName) {
        synchronized (lock) {
            final var library = pipRegistry.get(libraryName);
            if (null == library) {
                return List.of();
            }
            return library.attributeFinders().stream().map(AttributeFinderSpecification::attributeName).toList();
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
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<AttributeFinderSpecification> getAttributeMetatata() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<String> getAvailableLibraries() {
        synchronized (lock) {
            return pipRegistry.keySet().stream().toList();
        }
    }

    @Override
    public List<String> getEnvironmentAttributeCodeTemplates() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<String> getAttributeCodeTemplates() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<String, String> getDocumentedAttributeCodeTemplates() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<PolicyInformationPointDocumentation> getDocumentation() {
        // TODO Auto-generated method stub
        return null;
    }
    
    private PolicyInformationPointDocumentation toDocumentation(PolicyInformationPointSpecification specification) {
        final var finderDocs = new HashMap<String,String>();
        for(var finder:specification.attributeFinders()) {
            finderDocs.put(finder.attributeName(), )
        }
        final var documentation =  new  PolicyInformationPointDocumentation(specification.name(),specification.description(),specification.documentation(), finderDocs);
    }

}
