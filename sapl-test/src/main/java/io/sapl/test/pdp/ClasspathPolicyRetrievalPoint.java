package io.sapl.test.pdp;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.AuthorizationDecisionEvaluable;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.prp.PolicyRetrievalPoint;
import io.sapl.prp.PolicyRetrievalResult;
import io.sapl.test.utils.ClasspathHelper;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
public class ClasspathPolicyRetrievalPoint implements PolicyRetrievalPoint {

	private static final String POLICIES_FILE_GLOB_PATTERN = "*.sapl";
	private final Map<String, SAPL> documents;
	
	ClasspathPolicyRetrievalPoint(Path path, SAPLInterpreter interpreter) {
		this.documents = readPoliciesFromDirectory(path.toString(), interpreter);
	}

	private Map<String, SAPL> readPoliciesFromDirectory(String path, SAPLInterpreter interpreter) {
		Map<String, SAPL> documents = new HashMap<>();		
		Path policyDirectoryPath = ClasspathHelper.findPathOnClasspath(getClass(), path);
		log.debug("reading policies from directory {}", policyDirectoryPath);
		
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(policyDirectoryPath, POLICIES_FILE_GLOB_PATTERN)) {
			for (Path filePath : stream) {
				log.info("loading policy: {}", filePath.toAbsolutePath());
				SAPL sapl = interpreter.parse(Files.newInputStream(filePath));
				documents.put(sapl.getPolicyElement().getSaplName(), sapl);
			}
		} catch (IOException | PolicyEvaluationException e) {
			throw Exceptions.propagate(e);
		}
		return documents;
	}
	
	
	@Override
	public Flux<PolicyRetrievalResult> retrievePolicies(EvaluationContext subscriptionScopedEvaluationContext) {
		var retrieval = Mono.just(new PolicyRetrievalResult());
		for (SAPL document : documents.values()) {
			retrieval = retrieval.flatMap(retrievalResult -> document.matches(subscriptionScopedEvaluationContext).map(match -> {
				if (match.isError()) {
					return retrievalResult.withError();
				}
				if (!match.isBoolean()) {
					log.error("matching returned error. (Should never happen): {}", match.getMessage());
					return retrievalResult.withError();
				}
				if (match.getBoolean()) {
					return retrievalResult.withMatch(document);
				}
				return retrievalResult;
			}));
		}
		
		return Flux.from(retrieval).doOnNext(this::logMatching);
	}
	
    private void logMatching(PolicyRetrievalResult result) {
        if (result.getMatchingDocuments().isEmpty()) {
            log.trace("|-- Matching documents: NONE");
        } else {
            log.trace("|-- Matching documents:");
            for (AuthorizationDecisionEvaluable doc : result.getMatchingDocuments()) {
                log.trace("| |-- * {} ({})",
                        (doc instanceof SAPL) ? ((SAPL) doc).getPolicyElement().getSaplName() : doc.toString(),
                        (doc instanceof SAPL) ? ((SAPL) doc).getPolicyElement().getClass().getSimpleName()
                                : doc.toString());
            }
        }
        log.trace("|");
    }

	@Override
	public void dispose() {
	}

}
