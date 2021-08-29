package io.sapl.mavenplugin.test.coverage.report;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;

import io.sapl.grammar.sapl.Expression;
import io.sapl.grammar.sapl.Policy;
import io.sapl.grammar.sapl.PolicyElement;
import io.sapl.grammar.sapl.PolicySet;
import io.sapl.grammar.sapl.SaplPackage;
import io.sapl.grammar.sapl.Statement;
import io.sapl.mavenplugin.test.coverage.SaplTestException;
import io.sapl.mavenplugin.test.coverage.model.CoverageTargets;
import io.sapl.mavenplugin.test.coverage.model.SaplDocument;
import io.sapl.mavenplugin.test.coverage.report.model.LineCoveredValue;
import io.sapl.mavenplugin.test.coverage.report.model.SaplDocumentCoverageInformation;
import io.sapl.test.coverage.api.model.PolicyConditionHit;
import io.sapl.test.coverage.api.model.PolicyHit;
import io.sapl.test.coverage.api.model.PolicySetHit;

public class GenericCoverageReporter {
	private static final String ERROR_UNEXPECTED_ENUM_VALUE = "Unexpected Enum-Value \"%s\". Please consider reporting this bug to the library authors!";
	
	public List<SaplDocumentCoverageInformation> calcDocumentCoverage(Collection<SaplDocument> documents, CoverageTargets hits) {
		List<SaplDocumentCoverageInformation> documentsWithCoveringInfo = new LinkedList<>();
		for (SaplDocument saplDoc : documents) {
			var coveredDoc = new SaplDocumentCoverageInformation(saplDoc.getPathToDocument(), saplDoc.getLineCount());

			PolicyElement element = saplDoc.getSaplDocument().getPolicyElement();

			if (element.eClass().equals(SaplPackage.Literals.POLICY_SET)) {
				PolicySet set = (PolicySet) element;
				markLinesOfPolicySet(set, coveredDoc, hits);
			} else if (element.eClass().equals(SaplPackage.Literals.POLICY)) {
				Policy policy = (Policy) element;
				markLinesOfPolicy("", policy, coveredDoc, hits);
			} else {
				throw new SaplTestException("Error: Unknown Subtype of " + PolicyElement.class);
			}

			documentsWithCoveringInfo.add(coveredDoc);
		}
		return documentsWithCoveringInfo;
	}
	
	private void addLinesToSet(Set<Integer> lines, int startLineNumber, int endLineNumber) {
		for(int i = startLineNumber; i <= endLineNumber; i++) {
			lines.add(i);
		}
	}

	private void markLinesOfPolicySet(PolicySet set, SaplDocumentCoverageInformation coverage, CoverageTargets hits) {
		//calculate lines of policyset to be marked
		Set<Integer> linesOfPolicySet = new HashSet<>();

		// mark first line of the Node of the complete PolicySet. First line usually
		// contains the set-Token and the PolicySet-Name
		INode nodeSet = NodeModelUtils.getNode(set);
		addLinesToSet(linesOfPolicySet, nodeSet.getStartLine(), nodeSet.getStartLine());

		// mark the algorithm
		INode nodeAlg = NodeModelUtils.getNode(set.getAlgorithm());
		addLinesToSet(linesOfPolicySet, nodeAlg.getStartLine(), nodeAlg.getEndLine());

		// mark all value definitions
		for (var valueDefinition : set.getValueDefinitions()) {
			INode nodeValDef = NodeModelUtils.getNode(valueDefinition);
			addLinesToSet(linesOfPolicySet, nodeValDef.getStartLine(), nodeValDef.getEndLine());
		}

		// mark the target expression
		Expression target = set.getTargetExpression();
		if (target != null) {
			INode nodeTarget = NodeModelUtils.getNode(target);
			addLinesToSet(linesOfPolicySet, nodeTarget.getStartLine(), nodeTarget.getEndLine());
		}
		
		
		

		// check if policy is hit
		boolean isSetHit = hits.isPolicySetHit(new PolicySetHit(set.getSaplName()));
		
		//mark Lines
		if(isSetHit) {
			markLines(coverage, linesOfPolicySet, LineCoveredValue.FULLY, 1, 1);
		} else {
			markLines(coverage, linesOfPolicySet, LineCoveredValue.NEVER, 0, 1);
		}
		

		// evaluate coverage for every policy in this set
		for (Policy policy : set.getPolicies()) {
			markLinesOfPolicy(set.getSaplName(), policy, coverage, hits);
		}
	}

	private void markLinesOfPolicy(String policySetName, Policy policy, SaplDocumentCoverageInformation coverage, CoverageTargets hits) {
		//calculate lines of policy to be marked
		Set<Integer> linesOfPolicy = new HashSet<>();
		
		// mark first line of the Node of the complete Policy. First line usually
		// contains the policy-Token and the Policy-Name
		INode nodePolicy = NodeModelUtils.getNode(policy);
		addLinesToSet(linesOfPolicy, nodePolicy.getStartLine(), nodePolicy.getStartLine());

		// mark the target expression
		Expression target = policy.getTargetExpression();
		if (target != null) {
			INode nodeTarget = NodeModelUtils.getNode(target);
			addLinesToSet(linesOfPolicy, nodeTarget.getStartLine(), nodeTarget.getEndLine());
		}

		
		// check if policy is hit
		boolean isPolicyHit = hits.isPolicyHit(new PolicyHit(policySetName, policy.getSaplName()));
		
		//mark Lines
		if(isPolicyHit) {
			markLines(coverage, linesOfPolicy, LineCoveredValue.FULLY, 1, 1);
		} else {
			markLines(coverage, linesOfPolicy, LineCoveredValue.NEVER, 0, 1);
		}

		
		// evaluate coverage for every statement in this policy
		if(policy.getBody() != null) {
			List<Statement> statements = policy.getBody().getStatements();
			boolean isLastStatementHit = isPolicyHit;
			for (int i = 0; i < statements.size(); i++) {
				isLastStatementHit = markLinesOfPolicyCondition(policySetName, policy.getSaplName(), i,
						statements.get(i), isLastStatementHit, coverage, hits);
			}
		}
		
	}

	private boolean markLinesOfPolicyCondition(String policySetName, String policyName, int statementId,
			Statement statement, boolean isLastStatementHit, SaplDocumentCoverageInformation coverage, CoverageTargets hits) {
		boolean isThisStatementHit = true;
		INode node = NodeModelUtils.getNode(statement);

		// if this statement is of type CONDITION
		if (statement.eClass().equals(SaplPackage.Literals.CONDITION)) {
			// get hit types
			boolean isPositivHit = hits.isPolicyConditionHit(new PolicyConditionHit(policySetName, policyName, statementId, true));
			boolean isNegativHit = hits.isPolicyConditionHit(new PolicyConditionHit(policySetName, policyName, statementId, false));
			
			//when this statement was once positive evaluated, then the next statement will have been evaluated too
			//used for Value-Definition statements (see below)
			isThisStatementHit = isPositivHit;

			// if there was a positiv and negativ hit -> fully covered
			if (isPositivHit && isNegativHit) {
				markConditionFULLY(coverage, node.getStartLine(), node.getEndLine());
			// if only one of both was hit -> partly covered
			} else if (isPositivHit || isNegativHit) {
				markConditionPARTLY(coverage, node.getStartLine(), node.getEndLine());
			// evaluation never reached this condition
			} else {
				markConditionNEVER(coverage, node.getStartLine(), node.getEndLine());
			}

			// if this statement is of type VALUE_DEFINITION
		} else if (statement.eClass().equals(SaplPackage.Literals.VALUE_DEFINITION)) {
			// mark a value definition if the previous statement evaluated to true
			if (isLastStatementHit) {
				markValueFULLY(coverage, node.getStartLine(), node.getEndLine());
			} else {
				markValueNEVER(coverage, node.getStartLine(), node.getEndLine());
			}
		} else {
			throw new SaplTestException("Error: Unknown Subtype of " + Statement.class
					+ ". Please consider reporting this bug to the authors!");
		}

		return isThisStatementHit;
	}

	private void markLines(SaplDocumentCoverageInformation coverage, Set<Integer> lines,
			LineCoveredValue value, int coveredBranches, int branchesToCover) {
		for (Integer i : lines) {
			coverage.markLine(i, value, coveredBranches, branchesToCover);
		}
	}

	private void markConditionFULLY(SaplDocumentCoverageInformation coverage, int linesStart, int linesEnd) {
		for (int i = linesStart; i <= linesEnd; i++) {
			var line = coverage.getLine(i);
			switch (line.getCoveredValue()) {
			case FULLY:
				// don't do anything. Already FULLY
				coverage.markLine(i, LineCoveredValue.FULLY, line.getCoveredBranches() + 2, line.getBranchesToCover() + 2);
				break;
			case PARTLY:
				// don't change LineCoveredValue. If previous statement only PARTLY, than whole line partly.
				// But mark this part of the line as fully covered by adding +2 to coveredBranches and branchesToCover
				coverage.markLine(i, LineCoveredValue.PARTLY, line.getCoveredBranches() + 2, line.getBranchesToCover() + 2);
				break;
			case NEVER:
				// if this condition or the condition evaluated before on the same line was never hit, than this or the next condition cannot have been evaluated
				// thus this change from NEVER -> FULLY cannot happen
				throw new SaplTestException(
						String.format(ERROR_UNEXPECTED_ENUM_VALUE, line.getCoveredValue()));
			case UNINTERESTING:
				// mark this line as fully covered by adding +2 to coveredBranches and branchesToCover
				coverage.markLine(i, LineCoveredValue.FULLY, line.getCoveredBranches() + 2, line.getBranchesToCover() + 2);
				break;
			}
		}
	}

	private void markConditionPARTLY(SaplDocumentCoverageInformation coverage, int linesStart, int linesEnd) {
		for (int i = linesStart; i <= linesEnd; i++) {
			var line = coverage.getLine(i);
			switch (coverage.getLine(i).getCoveredValue()) {
			case FULLY:
				coverage.markLine(i, LineCoveredValue.PARTLY, line.getCoveredBranches() + 1, line.getBranchesToCover() + 2);
				break;
			case PARTLY:
				// only add branches
				coverage.markLine(i, LineCoveredValue.PARTLY, line.getCoveredBranches() + 1, line.getBranchesToCover() + 2);
				break;
			case NEVER:
				// if this condition or the condition evaluated before on the same line was never hit, than this or the next condition cannot be evaluated too
				// thus this change from NEVER -> PARTLY cannot happen
				throw new SaplTestException(
						String.format(ERROR_UNEXPECTED_ENUM_VALUE, line.getCoveredValue()));
			case UNINTERESTING:
				coverage.markLine(i, LineCoveredValue.PARTLY, line.getCoveredBranches() + 1, line.getBranchesToCover() + 2);
				break;
			}
		}
	}

	private void markConditionNEVER(SaplDocumentCoverageInformation coverage, int linesStart, int linesEnd) {
		for (int i = linesStart; i <= linesEnd; i++) {
			var line = coverage.getLine(i);
			switch (line.getCoveredValue()) {
			case FULLY:
				coverage.markLine(i, LineCoveredValue.PARTLY, line.getCoveredBranches() + 0, line.getBranchesToCover() + 2);
				break;
			case PARTLY:
				// only add branches
				coverage.markLine(i, LineCoveredValue.PARTLY, line.getCoveredBranches() + 0, line.getBranchesToCover() + 2);
				break;
			case NEVER:
				// only add branches
				coverage.markLine(i, LineCoveredValue.PARTLY, line.getCoveredBranches() + 0, line.getBranchesToCover() + 2);
				break;
			case UNINTERESTING:
				coverage.markLine(i, LineCoveredValue.NEVER, line.getCoveredBranches() + 0, line.getBranchesToCover() + 2);
				break;
			}
		}
	}
	
	private void markValueFULLY(SaplDocumentCoverageInformation coverage, int linesStart, int linesEnd) {
		for (int i = linesStart; i <= linesEnd; i++) {
			var line = coverage.getLine(i);
			switch (line.getCoveredValue()) {
			case FULLY:
				//nothing to do
				break;
			case PARTLY:
				// nothing to do
				break;
			case NEVER:
				// if this value definition or the value definition evaluated before on the same line was never hit, than this value definition cannot be evaluated too
				// thus this change from NEVER -> FULLY cannot happen
				throw new SaplTestException(
						String.format(ERROR_UNEXPECTED_ENUM_VALUE, line.getCoveredValue()));
			case UNINTERESTING:
				coverage.markLine(i, LineCoveredValue.FULLY, line.getCoveredBranches(), line.getBranchesToCover());
				break;
			}
		}
	}

	private void markValueNEVER(SaplDocumentCoverageInformation coverage, int linesStart, int linesEnd) {
		for (int i = linesStart; i <= linesEnd; i++) {
			var line = coverage.getLine(i);
			switch (line.getCoveredValue()) {
				case FULLY:
					// when previous condition on this line is fully covered than this value definition must have been evaluated
					throw new SaplTestException(
							String.format(ERROR_UNEXPECTED_ENUM_VALUE, line.getCoveredValue()));
				case PARTLY:
					//nothing to do
					// if previous condition on the same line is partly hit (false hit) and this value definition is thus not evaluated -> PARTLY stays
					break;
				case NEVER:
					// nothing to do
					break;
				case UNINTERESTING:
					coverage.markLine(i, LineCoveredValue.NEVER, line.getCoveredBranches(), line.getBranchesToCover());
					break;
			}
		}
	}
}
