package io.sapl.api.documentation;

import java.util.List;

import lombok.NonNull;

public record PolicyInformationPointDocumentation(@NonNull String name, @NonNull String descriptionMarkdown,
        @NonNull String documentationMarkdown, @NonNull List<AttributeDocumentation> attributes) {}
