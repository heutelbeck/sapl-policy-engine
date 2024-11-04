package io.sapl.api.documentation;

import lombok.NonNull;

public record AttributeDocumentation(@NonNull String namespace, @NonNull String pipName, @NonNull AttributeType type,
        @NonNull String codeTemplate, @NonNull String documentationMarkdown) {}
