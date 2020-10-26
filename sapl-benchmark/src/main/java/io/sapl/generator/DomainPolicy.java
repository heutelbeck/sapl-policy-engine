package io.sapl.generator;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class DomainPolicy {

    private final String policyName;
    private final String policyContent;
    private final String fileName;

    @Data
    @RequiredArgsConstructor
    public static class DomainPolicyAdvice {

        private final String advice;
    }

    @Data
    @RequiredArgsConstructor
    public static class DomainPolicyBody {

        private final String body;

    }

    @Data
    @RequiredArgsConstructor
    public static class DomainPolicyObligation {

        private final String obligation;
    }

    @Data
    @RequiredArgsConstructor
    public static class DomainPolicyTransformation {

        private final String transformation;
    }
}
