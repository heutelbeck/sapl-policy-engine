package io.sapl.attributes.broker.api;

import java.util.Set;

import lombok.NonNull;

public record PolicyInformationPointSpecification(@NonNull String name, @NonNull String description,
        @NonNull String documentation, @NonNull Set<AttributeFinderSpecification> attributeFinders) {}
