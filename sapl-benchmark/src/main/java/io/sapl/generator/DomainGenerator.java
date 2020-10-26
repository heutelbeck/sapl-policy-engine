package io.sapl.generator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import io.sapl.generator.DomainPolicy.DomainPolicyObligation;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@Component
@RequiredArgsConstructor
public class DomainGenerator {

    private static final String TAB_STRING = "\t\t";

    private final DomainData domainData;

    private final DomainUtil domainUtil;

    public void generateDomainPoliciesWithSeed(long newSeed, String policyPath) {
        domainData.initDiceWithSeed(newSeed);
        generateDomainPolicies(policyPath);
    }

    public void generateDomainPolicies(String policyPath) {
        List<DomainPolicy> domainPolicies = generatePolicies();

        log.info("policies TOTAL: {}", domainPolicies.size());

        domainUtil.printDomainPoliciesLimited(domainPolicies);
        domainUtil.writeDomainPoliciesToFilesystem(domainPolicies, policyPath);
    }

//    private DomainPolicy generateSystemPolicy(List<DomainResource> resources) {
//        return generateUnrestrictedPolicy(resources, Collections.singletonList(DomainRoles.ROLE_SYSTEM));
//    }
//
//    private DomainPolicy generateAdministratorPolicy(List<DomainResource> resources) {
//        DomainPolicy adminPolicy = generateUnrestrictedPolicy(resources,
//                Collections.singletonList(DomainRoles.ROLE_ADMIN));
//        //add logging obligation
//        StringBuilder policyBuilder = new StringBuilder(adminPolicy.getPolicyContent());
//        addObligationToPolicy(policyBuilder, DomainUtil.LOG_OBLIGATION);
//
//        return new DomainPolicy(adminPolicy.getPolicyName(), policyBuilder.toString(), adminPolicy.getFileName());
//    }


//    private DomainPolicy generateUnrestrictedPolicy(List<DomainResource> resources, List<DomainRole> roles) {
//        String policyName = String.format("%s full access on %s",
//                DomainUtil.getRoleNames(roles), DomainUtil.getResourceNames(resources));
//
//        String fileName = String.format("%s_%s", DomainUtil.getResourcesStringForFileName(resources),
//                DomainUtil.getRoleNames(roles));
//
//        StringBuilder policyBuilder = generateBasePolicy(policyName, resources);
//        addRolesToPolicy(policyBuilder, roles, resources.isEmpty());
//
//        return new DomainPolicy(policyName, policyBuilder.toString(), DomainUtil.sanitizeFileName(fileName));
//    }

//    private DomainPolicy generateActionSpecificPolicy(List<DomainResource> resources, List<String> actions,
//                                                      List<DomainRole> roles) {
//        String policyName = String.format("%s can perform %s on %s",
//                DomainUtil.getRoleNames(roles), actions, DomainUtil.getResourceNames(resources));
//
//        String fileName = String.format("%s_%s",
//                DomainUtil.getResourcesStringForFileName(resources),
//                DomainUtil.getRoleNames(roles));
//
//
//        return new DomainPolicy(policyName, generateBasePolicyWithActions(policyName, resources, actions, roles)
//                .toString(), DomainUtil.sanitizeFileName(fileName));
//    }


//    private DomainPolicy generateActionSpecificExtendedPolicy(List<DomainResource> resources, List<String> actions,
//                                                              ExtendedDomainRole extendedRole) {
//        String policyName = String.format("[%s] can perform %s on %s (extended: %s)",
//                extendedRole.getRole().getRoleName(), actions,
//                DomainUtil.getResourceNames(resources),
//                DomainUtil.getExtendedRoleIndicator(extendedRole));
//
//        String fileName = String
//                .format("%s_%s_extended",
//                        DomainUtil.getResourcesStringForFileName(resources),
//                        extendedRole.getRole().getRoleName());
//
//        StringBuilder policyBuilder = generateBasePolicyWithActions(policyName, resources, actions,
//                DomainRoles.toRole(Collections.singletonList(extendedRole)));
//
//        if (extendedRole.isBodyPresent()) {
//            addBodyToPolicy(policyBuilder, extendedRole.getBody());
//        }
//        if (extendedRole.isObligationPresent()) {
//            addObligationToPolicy(policyBuilder, extendedRole.getObligation());
//        }
//        if (extendedRole.isAdvicePresent()) {
//            addAdviceToPolicy(policyBuilder, extendedRole.getAdvice());
//        }
//        if (extendedRole.isTransformationPresent()) {
//            addTransformationToPolicy(policyBuilder, extendedRole.getTransformation());
//        }
//
//        return new DomainPolicy(policyName, policyBuilder.toString(), DomainUtil.sanitizeFileName(fileName));
//    }

    public StringBuilder generateEmptyPolicy(String policyName, boolean permit) {
        return new StringBuilder().append("policy \"").append(policyName).append("\"")
                .append(System.lineSeparator())
                .append(permit ? "permit " : "deny ");
    }

    public StringBuilder generateGeneralBasePolicy(String policyName, List<DomainRole> roles) {
        StringBuilder policyBuilder = new StringBuilder().append("policy \"").append(policyName).append("\"")
                .append(System.lineSeparator())
                .append("permit ");

        addRolesToPolicy(policyBuilder, roles, true);

        return policyBuilder;
    }

    private StringBuilder generateGeneralBasePolicyWithActions(String policyName, List<String> actions,
                                                               List<DomainRole> roles) {
        StringBuilder policyBuilder = generateGeneralBasePolicy(policyName, roles);
        addActionsToPolicy(policyBuilder, actions);

        return policyBuilder;
    }

    public StringBuilder generateBasePolicy(String policyName, List<DomainResource> resources) {
        StringBuilder policyBuilder = new StringBuilder().append("policy \"").append(policyName).append("\"")
                .append(System.lineSeparator())
                .append("permit ");

        boolean first = true;
        policyBuilder.append("(");
        for (DomainResource resource : resources) {
            if (first) first = false;
            else policyBuilder.append(" | ");
            policyBuilder.append(String.format("resource == \"%s\"", resource.getResourceName()));
        }
        policyBuilder.append(")");

        return policyBuilder;
    }

    private StringBuilder generateBasePolicyWithActions(String policyName, List<DomainResource> resources,
                                                        List<String> actions, List<DomainRole> roles) {
        StringBuilder policyBuilder = generateBasePolicy(policyName, resources);
        addRolesToPolicy(policyBuilder, roles, resources.isEmpty());
        addActionsToPolicy(policyBuilder, actions);

        return policyBuilder;
    }

    public void addRolesToPolicy(StringBuilder policyBuilder, List<DomainRole> roles, boolean emptyPermit) {
        if (roles.isEmpty()) return;

        policyBuilder.append(System.lineSeparator()).append(TAB_STRING);
        if (!emptyPermit) policyBuilder.append(" & ");
        policyBuilder.append("(");

        boolean firstRole = true;
        for (DomainRole role : roles) {
            if (firstRole) firstRole = false;
            else policyBuilder.append(" | ");
            policyBuilder.append(String.format("(\"%s\" in subject.authorities)", role.getRoleName()));
        }
        policyBuilder.append(")");
    }

    public void addActionsToPolicy(StringBuilder policyBuilder, List<String> actions) {
        if (actions.isEmpty()) return;

        policyBuilder.append(System.lineSeparator()).append(TAB_STRING).append(" & ").append("(");
        boolean firstAction = true;
        for (String action : actions) {
            if (firstAction) firstAction = false;
            else policyBuilder.append(" | ");
            policyBuilder.append(String.format("action == \"%s\"", action));
        }
        policyBuilder.append(")");
    }

//    private void addBodyToPolicy(StringBuilder policyBuilder, DomainPolicyBody body) {
//        policyBuilder.append(System.lineSeparator())
//                .append("where").append(System.lineSeparator())
//                .append(TAB_STRING).append(body.getBody());
//    }

    private void addObligationToPolicy(StringBuilder policyBuilder, DomainPolicyObligation obligation) {
        policyBuilder.append(System.lineSeparator())
                .append("obligation").append(System.lineSeparator())
                .append(TAB_STRING).append(obligation.getObligation());
    }

//    private void addAdviceToPolicy(StringBuilder policyBuilder, DomainPolicyAdvice advice) {
//        policyBuilder.append(System.lineSeparator())
//                .append("advice").append(System.lineSeparator())
//                .append(TAB_STRING).append(advice.getAdvice());
//    }
//
//    private void addTransformationToPolicy(StringBuilder policyBuilder, DomainPolicyTransformation transformation) {
//        policyBuilder.append(System.lineSeparator())
//                .append("transformation").append(System.lineSeparator())
//                .append(TAB_STRING).append(transformation.getTransformation());
//    }


    public List<DomainPolicy> generatePolicies() {

        List<DomainRole> allRoles = List.copyOf(domainData.getDomainRoles());
        log.debug("allRolesCount:{}", allRoles.size());
        List<DomainResource> allResources = List.copyOf(domainData.getDomainResources());
        log.debug("allResources:{}", allResources.size());
        List<DomainSubject> allSubjects = List.copyOf(domainData.getDomainSubjects());
        log.debug("allSubjects:{}", allSubjects.size());

        List<DomainResource> unrestrictedResources = allResources.stream().filter(DomainResource::isUnrestricted)
                .collect(Collectors.toList());
        List<DomainResource> restrictedResources = new ArrayList<>(allResources);
        restrictedResources.removeAll(unrestrictedResources);
        log.debug("generated {} resources (unrestricted={})", allResources.size(), unrestrictedResources.size());

        int newPolicyCount = 0;
        List<DomainPolicy> allPolicies = new ArrayList<>(generateSubjectSpecificPolicies(allSubjects));
        newPolicyCount = allPolicies.size();
        log.debug("generated {} subject specific policies", newPolicyCount);

//        allPolicies.addAll(generateLockedSubjectPolicies());
//        newPolicyCount = allPolicies.size() - newPolicyCount;
//        log.debug("generated {} policies for locked subjects", newPolicyCount);

        allPolicies.addAll(generatePoliciesForGeneralAccessRoles(allRoles));
        newPolicyCount = allPolicies.size() - newPolicyCount;
        log.debug("generated {} policies for general access roles", newPolicyCount);

        allPolicies.addAll(generatePoliciesForUnrestrictedResources(unrestrictedResources));
        newPolicyCount = allPolicies.size() - newPolicyCount;
        log.debug("generated {} policies for unrestricted resources", newPolicyCount);

        allPolicies.addAll(generatePoliciesForRestrictedResources(restrictedResources, allRoles));
        newPolicyCount = allPolicies.size() - newPolicyCount;
        log.debug("generated {} policies for restricted resources", newPolicyCount);


        return allPolicies;
    }


    private List<DomainPolicy> generatePoliciesForRestrictedResources(List<DomainResource> restrictedResources, List<DomainRole> allRoles) {
        List<DomainPolicy> policies = new ArrayList<>();

        List<DomainRole> rolesWithRestrictedAccess = allRoles.stream()
                .filter(role -> !role.isGeneralUnrestrictedAccess())
                .collect(Collectors.toList());

        for (DomainResource resource : restrictedResources) {
            collectAccessingRoles(rolesWithRestrictedAccess, resource);

            if (!resource.getFullAccessRoles().isEmpty())
                handleFullAccessRoles(policies, resource);

            if (!resource.getReadAccessRoles().isEmpty())
                handleReadAccessRoles(policies, resource);

            if (!resource.getCustomAccessRoles().isEmpty())
                handleCustomAccessRoles(policies, resource);

            resource.clearResourceAccessRoles();
        }

        return policies;
    }

    private void handleCustomAccessRoles(List<DomainPolicy> policies, DomainResource resource) {
        for (DomainRole customRole : resource.getCustomAccessRoles()) {
            String policyName = resource.getResourceName() + "_custom_" + customRole.getRoleName();

            if (resource.isExtensionRequired()) {
                policyName += "_extended";
            }
            StringBuilder policyBuilder = generateBasePolicyWithActions(policyName, Collections.singletonList(resource),
                    DomainActions.generateCustomActionList(domainData),
                    Collections.singletonList(customRole));

            if (resource.isExtensionRequired()) {
                addObligationToPolicy(policyBuilder, DomainUtil.LOG_OBLIGATION);
            }

            policies.add(new DomainPolicy(policyName, policyBuilder.toString(), policyName));
        }
    }

    private void handleReadAccessRoles(List<DomainPolicy> policies, DomainResource resource) {
        String policyName = resource.getResourceName() + "_read_roles";
        StringBuilder policyBuilder = generateBasePolicyWithActions(policyName, Collections
                .singletonList(resource), DomainActions.READ_ONLY.getActionList(), resource.getReadAccessRoles());

        List<DomainRole> extendedRoles = resource.getReadAccessRoles().stream()
                .filter(DomainRole::isExtensionRequired).collect(Collectors.toList());
        if (resource.isExtensionRequired()) {
            policyName += "_extended";
            addObligationToPolicy(policyBuilder, DomainUtil.LOG_OBLIGATION);

            policies.add(new DomainPolicy(policyName, policyBuilder.toString(), policyName));
        } else if (!extendedRoles.isEmpty()) {
            for (DomainRole extendedRole : extendedRoles) {

                StringBuilder rolePolicyBuilder = new StringBuilder(policyBuilder.toString());
                addObligationToPolicy(rolePolicyBuilder, DomainUtil.LOG_OBLIGATION);

                String rolePolicyName = resource.getResourceName() + "_read_" + extendedRole
                        .getRoleName() + "_extended";

                policies.add(new DomainPolicy(rolePolicyName, rolePolicyBuilder.toString(), rolePolicyName));
            }
        }


    }

    private void handleFullAccessRoles(List<DomainPolicy> policies, DomainResource resource) {
        String policyName = resource.getResourceName() + "_unrestricted-roles";
        StringBuilder policyBuilder = generateBasePolicy(policyName, Collections.singletonList(resource));
        addRolesToPolicy(policyBuilder, resource.getFullAccessRoles(), false);

        List<DomainRole> extendedRoles = resource.getFullAccessRoles().stream()
                .filter(DomainRole::isExtensionRequired).collect(Collectors.toList());
        if (resource.isExtensionRequired()) {
            policyName += "_extended";
            addObligationToPolicy(policyBuilder, DomainUtil.LOG_OBLIGATION);
        } else if (!extendedRoles.isEmpty()) {
            for (DomainRole extendedRole : extendedRoles) {
                StringBuilder rolePolicyBuilder = new StringBuilder(policyBuilder.toString());
                addObligationToPolicy(rolePolicyBuilder, DomainUtil.LOG_OBLIGATION);
                String rolePolicyName = resource.getResourceName() + "_unrestricted_" + extendedRole
                        .getRoleName();

                policies.add(new DomainPolicy(rolePolicyName, rolePolicyBuilder.toString(), rolePolicyName));
            }
        }

        policies.add(new DomainPolicy(policyName, policyBuilder.toString(), policyName));
    }

    private void collectAccessingRoles(List<DomainRole> rolesWithRestrictedAccess, DomainResource resource) {
        for (DomainRole role : rolesWithRestrictedAccess) {
            boolean fullAccessOnResource = domainData
                    .rollIsLowerThanProbability(domainData.getProbabilityFullAccessOnResource());
            if (fullAccessOnResource) {
                resource.getFullAccessRoles().add(role);
                continue;
            }
            boolean readAccessOnResource = domainData
                    .rollIsLowerThanProbability(domainData.getProbabilityReadAccessOnResource());
            if (readAccessOnResource) {
                resource.getReadAccessRoles().add(role);
                continue;
            }
            resource.getCustomAccessRoles().add(role);
        }
    }

    private List<DomainPolicy> generatePoliciesForUnrestrictedResources(List<DomainResource> unrestrictedResources) {
        List<DomainPolicy> policies = new ArrayList<>();

        policies.add(new DomainPolicy("unrestricted-resources",
                generateBasePolicy("unrestricted-resources", unrestrictedResources).toString(),
                "unrestricted-resources"
        ));

        return policies;
    }

    private List<DomainPolicy> generatePoliciesForGeneralAccessRoles(List<DomainRole> allRoles) {
        List<DomainPolicy> policies = new ArrayList<>();

        List<DomainRole> unrestrictedRoles = allRoles.stream().filter(DomainRole::isGeneralUnrestrictedAccess)
                .collect(Collectors.toList());
        List<DomainRole> unrestrictedExtensionRoles = unrestrictedRoles.stream().filter(DomainRole::isExtensionRequired)
                .collect(Collectors.toList());
        unrestrictedRoles.removeAll(unrestrictedExtensionRoles);

        if (!unrestrictedRoles.isEmpty())
            policies.add(new DomainPolicy("general unrestricted roles",
                    generateGeneralBasePolicy("general unrestricted roles", unrestrictedRoles).toString(),
                    "general_unrestricted_roles"
            ));
        //TODO extendedRoles

        List<DomainRole> readRoles = allRoles.stream().filter(DomainRole::isGeneralReadAccess)
                .collect(Collectors.toList());
        List<DomainRole> readExtensionRoles = readRoles.stream().filter(DomainRole::isExtensionRequired)
                .collect(Collectors.toList());
        readRoles.removeAll(readExtensionRoles);

        if (!readRoles.isEmpty())
            policies.add(new DomainPolicy("general read roles",
                    generateGeneralBasePolicyWithActions("general_read_roles", DomainActions.READ_ONLY
                            .getActionList(), readRoles).toString(), "general_read_roles"
            ));
        //TODO extendedRoles

        List<DomainRole> customRoles = allRoles.stream().filter(DomainRole::isGeneralCustomAccess)
                .collect(Collectors.toList());
        List<DomainRole> customExtensionRoles = customRoles.stream().filter(DomainRole::isExtensionRequired)
                .collect(Collectors.toList());
        customRoles.removeAll(customExtensionRoles);

        if (!customRoles.isEmpty())
            policies.addAll(customRoles.stream()
                    .map(customRole -> new DomainPolicy("general_custom_" + customRole.getRoleName(),
                            generateGeneralBasePolicyWithActions("general_custom_" + customRole
                                    .getRoleName(), DomainActions
                                    .generateCustomActionList(domainData), Collections.singletonList(customRole)
                            ).toString(), "general_custom_role_" + customRole.getRoleName())
                    ).collect(Collectors.toList()));
        //TODO extendedRoles

        return policies;
    }


    private List<DomainPolicy> generateSubjectSpecificPolicies(List<DomainSubject> allSubjects) {
        List<DomainPolicy> policies = new ArrayList<>();

        for (DomainSubject subject : allSubjects) {
            String subjectName = subject.getSubjectName();
            for (int i = 0; i < domainData.getNumberOfRolesPerSubject(); i++) {
                String policyName = String.format("policy %d for %s", i, subjectName);

                StringBuilder policyBuilder = generateEmptyPolicy(policyName, true);
                policyBuilder.append(String.format("(resource == \"%s\")", subjectName))
                        .append(System.lineSeparator())
                        .append(TAB_STRING).append(" & ")
                        .append(String.format("(\"%s\" == subject.name)", subjectName));

                policies.add(new DomainPolicy(policyName, policyBuilder.toString(), subjectName));
            }
        }

        return policies;
    }

//    private List<DomainPolicy> generateLockedSubjectPolicies() {
//        List<DomainPolicy> policies = new ArrayList<>();
//
//        for (int i = 0; i < domainData.getNumberOfLockedSubjects(); i++) {
//            String subjectName = String.format("subject%03d", i);
//            String policyName = "policy for locked" + subjectName;
//
//            StringBuilder policyBuilder = generateEmptyPolicy(policyName, false);
//            policyBuilder.append(String.format("(\"%s\" == subject.name)", subjectName));
//            policies.add(new DomainPolicy(policyName, policyBuilder.toString(), subjectName+ "_locked"));
//        }
//
//        return policies;
//    }


}
