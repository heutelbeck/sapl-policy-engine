package io.sapl.interpreter.pip;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import io.sapl.interpreter.functions.SchemaTemplates;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Metadata for attribute finders.
 */
@Data
@AllArgsConstructor
public class AttributeFinderMetadata implements LibraryEntryMetadata {

	Object  policyInformationPoint;
	Method  function;
	String  libraryName;
	String  functionName;
	String  functionSchema;
	boolean environmentAttribute;
	boolean attributeWithVariableParameter;
	boolean varArgsParameters;
	int     numberOfParameters;

	@Override
	public String getDocumentationCodeTemplate() {
		var sb                             = new StringBuilder();
		var indexOfParameterBeingDescribed = 0;

		if (!isEnvironmentAttribute())
			sb.append(describeParameterForDocumentation(indexOfParameterBeingDescribed++)).append('.');

		if (isAttributeWithVariableParameter())
			indexOfParameterBeingDescribed++;

		sb.append('<').append(fullyQualifiedName());

		appendParameterList(sb, indexOfParameterBeingDescribed, this::describeParameterForDocumentation);

		sb.append('>');
		return sb.toString();
	}

	@Override
	public String getFunctionSchema() {
		return functionSchema;
	}

	@Override
	public String getCodeTemplate() {
		var sb                             = new StringBuilder();
		var indexOfParameterBeingDescribed = 0;

		if (!isEnvironmentAttribute())
			indexOfParameterBeingDescribed++;

		if (isAttributeWithVariableParameter())
			indexOfParameterBeingDescribed++;

		sb.append(fullyQualifiedName());

		appendParameterList(sb, indexOfParameterBeingDescribed, this::getParameterName);

		sb.append('>');
		return sb.toString();
	}

	@Override
	public List<String> getSchemaTemplates() {
		StringBuilder sb;
		var schemaTemplates = new ArrayList<String>();
		var funCodeTemplate = getCodeTemplate();
		var schema = getFunctionSchema();
		if (schema.length() > 0){
			SchemaTemplates schemaTemplate = new SchemaTemplates();
			var paths = schemaTemplate.schemaTemplates(schema);
			for (var path : paths){
				sb = new StringBuilder();
				sb.append(funCodeTemplate).append(".").append(path);
				schemaTemplates.add(sb.toString());
			}
		}
		return schemaTemplates;
	}

}