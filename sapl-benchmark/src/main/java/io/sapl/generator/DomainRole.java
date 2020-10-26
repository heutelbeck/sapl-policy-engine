package io.sapl.generator;

import io.sapl.generator.DomainPolicy.DomainPolicyAdvice;
import io.sapl.generator.DomainPolicy.DomainPolicyBody;
import io.sapl.generator.DomainPolicy.DomainPolicyObligation;
import io.sapl.generator.DomainPolicy.DomainPolicyTransformation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

@Data
@RequiredArgsConstructor
public class DomainRole {

    private final String roleName;
    private final boolean generalUnrestrictedAccess;
    private final boolean generalReadAccess;
    private final boolean generalCustomAccess;
    private final boolean extensionRequired;

    public DomainRole(String roleName) {
        this(roleName, false, false, false, false);
    }

    public static class DomainRoles {

        public static DomainRole ROLE_AUTHORIZED = new DomainRole("authorized", false, false, false, false);
        public static DomainRole ROLE_ADMIN = new DomainRole("admin", true, false, false, true);
        public static DomainRole ROLE_SYSTEM = new DomainRole("system", true, false, false, false);

        public static DomainRole findByName(List<DomainRole> roleList, String roleName) {
            return roleList.stream()
                    .filter(domainRole -> domainRole.getRoleName().equalsIgnoreCase(roleName))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("missing role: " + roleName));
        }

        public static List<DomainRole> toRole(List<ExtendedDomainRole> rolesForAction) {
            return rolesForAction.stream().map(ExtendedDomainRole::getRole).collect(Collectors.toList());
        }

    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class ExtendedDomainRole {

        private DomainRole role;

        @Builder.Default
        private DomainPolicyBody body = null;
        @Builder.Default
        private DomainPolicyObligation obligation = null;
        @Builder.Default
        private DomainPolicyAdvice advice = null;
        @Builder.Default
        private DomainPolicyTransformation transformation = null;


        public ExtendedDomainRole(DomainRole role) {
            this.role = role;
        }

        public boolean isBodyPresent() {

            return this.body != null;
        }

        public boolean isObligationPresent() {
            return this.obligation != null;
        }

        public boolean isAdvicePresent() {
            return this.advice != null;
        }

        public boolean isTransformationPresent() {
            return this.transformation != null;
        }


    }

}
