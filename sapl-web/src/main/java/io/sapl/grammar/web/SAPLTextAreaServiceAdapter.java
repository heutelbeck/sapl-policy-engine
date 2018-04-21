package io.sapl.grammar.web;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistContext;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistEntry;
import org.eclipse.xtext.ide.editor.contentassist.IIdeContentProposalAcceptor;
import org.eclipse.xtext.ide.editor.contentassist.IdeContentProposalCreator;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SAPLTextAreaServiceAdapter implements FilterFunctionsProposalProvider {

	// @Autowired
	// private AuthzContextBuilderService authzContectBuilder;

	private static SAPLTextAreaServiceAdapter instance;

	private Set<String> availableFunctionLibraries;

	public static SAPLTextAreaServiceAdapter getInstance() {
		return instance;
	}

	@PostConstruct
	public void init() {
		instance = this;
		availableFunctionLibraries = new HashSet<>();
		// availableFunctionLibraries =
		// authzContectBuilder.getAvailableLibraries();
		// FunctionContext context =
		// authzContectBuilder.buildFunctionContext(availableFunctionLibraries);
		// Collection<LibraryDocumentation> docs = context.getDocumentation();
		// for (LibraryDocumentation doc : docs) {
		// System.out.println("############## " + doc.getName() + " " +
		// doc.getDescription());
		//
		// Map<String, String> dMap = doc.getDocumentation();
		// for (String s1 : dMap.keySet()) {
		// System.out.println("################## " + s1 + " > " +
		// dMap.get(s1));
		// }
		// }

	}

	@Override
	public void appendFilterFunctionNames(ContentAssistContext context, IIdeContentProposalAcceptor acceptor,
			IdeContentProposalCreator proposalCreator) {
		EObject prev = context.getPreviousModel();
		LOGGER.error("prev: {}", prev.eClass());
	}

	@Override
	public void appendFilterLibraryNames(ContentAssistContext context, IIdeContentProposalAcceptor acceptor,
			IdeContentProposalCreator proposalCreator) {
		for (String libName : availableFunctionLibraries) {
			ContentAssistEntry entry = proposalCreator.createProposal(libName, context);
			acceptor.accept(entry, 0);
		}
	}
}
