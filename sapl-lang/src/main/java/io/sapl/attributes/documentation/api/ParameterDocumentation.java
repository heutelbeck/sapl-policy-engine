package io.sapl.attributes.documentation.api;

import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.NonNull;

public record ParameterDocumentation(@NonNull String name, @NonNull List<TypeOption> allowedTypes, boolean isVarArgs) {

    private final static ObjectMapper MAPPER = new ObjectMapper();

    public String parameterDescription() {
        final var sb = new StringBuilder();
        sb.append(name);
        if (isVarArgs) {
            sb.append("...");
        }
        return sb.toString();
    }

    public String typedParameterDescription() {
        final var sb = new StringBuilder();
        sb.append(parameterDescription());
        sb.append(" [");
        if (allowedTypes.isEmpty()) {
            sb.append(" [ANY]");
        } else {
            sb.append(allowedTypes.stream().map(TypeOption::type).collect(Collectors.joining("|")));
        }
        sb.append("]");
        return sb.toString();
    }

    public String parameterDocumentationMarkdown() {
        final var sb = new StringBuilder();
        sb.append("Name: ");
        sb.append(typedParameterDescription());
        sb.append("\n");
        sb.append(allowedSchemaMarkdown());
        return sb.toString();
    }

    public String allowedSchemaMarkdown() {
        final var schemaOptions = allowedTypes.stream().filter(t -> "JSON Schema".equals(t.type())).toList();
        if (schemaOptions.isEmpty()) {
            return "";
        }
        final var sb = new StringBuilder();
        sb.append("# Allowed JSON Schema\n");
        for (var t : schemaOptions) {
            sb.append("```JSON\n");
            try {
                MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(t.schema());
            } catch (JsonProcessingException e) {
                sb.append("Error processing schema: ");
                sb.append(e.getMessage());
                sb.append('\n');
            }
            sb.append("```\n");
        }
        return sb.toString();
    }

}
