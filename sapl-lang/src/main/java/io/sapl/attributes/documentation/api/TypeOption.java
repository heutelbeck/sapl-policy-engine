package io.sapl.attributes.documentation.api;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.NonNull;

public record TypeOption(@NonNull String type, JsonNode schema) {}
