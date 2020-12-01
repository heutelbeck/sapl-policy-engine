/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.generator;

import io.sapl.generator.DomainPolicy.DomainPolicyBody;
import io.sapl.generator.DomainPolicy.DomainPolicyObligation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class DomainUtil {

    private static final AtomicInteger policyCounter = new AtomicInteger();
    private static final AtomicInteger roleCounter = new AtomicInteger();
    private static final AtomicInteger resourceCounter = new AtomicInteger();
    private static final AtomicInteger appendixCounter = new AtomicInteger();

    private final boolean cleanDirectory;

    public static final DomainPolicyObligation LOG_OBLIGATION = new DomainPolicyObligation("\"logging:log_access\"");
    public static final DomainPolicyBody TREATING_BODY = new DomainPolicyBody("subject in resource.<patient.treating>;");
    public static final DomainPolicyBody RELATIVE_BODY = new DomainPolicyBody("subject in resource.<patient.relatives>;");
    public static final DomainPolicyBody OWN_DATA_BODY = new DomainPolicyBody("subject.id == resource.patient;");


    public DomainUtil(boolean cleanDirectory) {
        this.cleanDirectory = cleanDirectory;
    }

    public void writeDomainPoliciesToFilesystem(List<DomainPolicy> domainPolicies, String policyPath) {
        log.info("writing policies to folder: {}", policyPath);

        File policyDir = new File(policyPath);
        boolean directoryCreated = policyDir.mkdir();
        if (!directoryCreated) throw new RuntimeException("policy directory could not be created");


        log.debug("before clean fileCount:{}", Objects.requireNonNull(policyDir.listFiles()).length);
        if (cleanDirectory) cleanPolicyDirectory(policyPath);
        log.debug("after clean fileCount:{}", Objects.requireNonNull(policyDir.listFiles()).length);


        for (DomainPolicy domainPolicy : domainPolicies) {
            writePolicyToFile(domainPolicy, policyPath);
        }

        log.debug("after write policy fileCount:{}", Objects.requireNonNull( policyDir.listFiles()).length);
    }

    public void cleanPolicyDirectory(String policyPath) {
        log.info("removing existing policies in output directory");
        try {
            FileUtils.cleanDirectory(new File(policyPath));
        } catch (IOException e) {
            log.error("error while cleaning the directory", e);
        }
    }

    public void printDomainPoliciesLimited(List<DomainPolicy> domainPolicies) {
        log.trace("#################### POLICIES ####################");
        for (DomainPolicy domainPolicy : domainPolicies) {
            log.trace("{}--------------------------------------------------{}{}{}--------------------------------------------------",
                    System.lineSeparator(), System.lineSeparator(), domainPolicy.getPolicyContent(), System
                            .lineSeparator());
        }
    }

    public void writePolicyToFile(DomainPolicy policy, String policyPath) {
        String policyFileName = String
                .format("%s/%03d_%s.sapl", policyPath, DomainUtil.getNextPolicyCount(), policy.getFileName());
        log.trace("writing policy file: {}", policyFileName);

        try (PrintWriter writer = new PrintWriter(policyFileName, StandardCharsets.UTF_8.name())) {
            writer.println(policy.getPolicyContent());
        } catch (FileNotFoundException | UnsupportedEncodingException e) {
            log.error("writing policy file failed", e);
        }
    }


    //    public static List<String> getRoleNames(List<DomainRole> roles) {
    //        return roles.stream().map(DomainRole::getRoleName).collect(Collectors.toList());
    //    }
    //
    //    public static List<String> getResourceNames(List<DomainResource> resources) {
    //        return resources.stream().map(DomainResource::getResourceName).collect(Collectors.toList());
    //    }
    //
    //    public static String getResourcesStringForFileName(List<DomainResource> resources) {
    //        String firstResourceName = resources.get(0).getResourceName();
    //        if (resources.size() < 2) return firstResourceName;
    //        if (!firstResourceName.contains(".")) return firstResourceName;
    //
    //        String[] split = firstResourceName.split("\\.");
    //        return String.format("%ss.%s", split[0], split[1]);
    //    }
    //
    //    public static List<String> getExtendedRoleNames(List<ExtendedDomainRole> roles) {
    //        return roles.stream().map(ExtendedDomainRole::getRole).map(DomainRole::getRoleName)
    //                .collect(Collectors.toList());
    //    }
    //
    //    public static String getExtendedRoleIndicator(ExtendedDomainRole role) {
    //        return String.format("b=%d,o=%d,a=%d,t=%d",
    //                role.isBodyPresent() ? 1 : 0, role.isObligationPresent() ? 1 : 0, role.isAdvicePresent() ? 1 : 0, role
    //                        .isTransformationPresent() ? 1 : 0);
    //    }


    public static int getNextPolicyCount() {
        return policyCounter.getAndIncrement();
    }

    public static int getNextRoleCount() {
        return roleCounter.getAndIncrement();
    }

    public static int getNextResourceCount() {
        return resourceCounter.getAndIncrement();
    }

    public static int getNextAppendixCount() {
        return appendixCounter.getAndIncrement();
    }

    public static String sanitizeFileName(String fileName) {
        return fileName.toLowerCase().replaceAll("\\.", "-")
                .replaceAll("[\\[\\]]", "").replace(", ", "-");
    }

    public static String getIOrDefault(List<String> list, int i, String defaultStr) {
        try {
            return list.get(i);
        } catch (Exception e) {
            return defaultStr;
        }
    }


}
