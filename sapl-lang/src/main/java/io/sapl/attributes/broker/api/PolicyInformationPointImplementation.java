package io.sapl.attributes.broker.api;

import java.util.Map;

import lombok.NonNull;

public record PolicyInformationPointImplementation(@NonNull PolicyInformationPointSpecification specification,
        @NonNull Map<AttributeFinderSpecification, AttributeFinder> implementsations) {}
