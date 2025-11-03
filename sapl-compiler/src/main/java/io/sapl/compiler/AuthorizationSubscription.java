package io.sapl.compiler;

import io.sapl.api.v2.Value;
import lombok.NonNull;

public record AuthorizationSubscription(@NonNull Value subject,@NonNull  Value action,@NonNull  Value resource,@NonNull  Value environment) {
}
