package io.sapl.prp.inmemory.indexed;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.Request;
import io.sapl.api.prp.ParsedDocumentIndex;
import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.grammar.sapl.Expression;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.variables.VariableContext;

public class FastParsedDocumentIndex implements ParsedDocumentIndex {

	private FunctionContext bufferCtx;

	private final IndexCreationStrategy creationStrategy = new FastIndexCreationStrategy();

	private final Queue<Map.Entry<String, SAPL>> documentChanges = new ConcurrentLinkedQueue<>();

	private IndexContainer documentIndex;

	private final AtomicBoolean documentSwitch = new AtomicBoolean(true);

	private FunctionContext functionCtx;

	private final Lock functionCtxReadLock;

	private final AtomicBoolean functionCtxSwitch = new AtomicBoolean(true);

	private final Lock functionCtxWriteLock;

	private final CountDownLatch initLatch = new CountDownLatch(1);

	private final AtomicBoolean initSwitch = new AtomicBoolean(true);

	private final AtomicBoolean liveSwitch = new AtomicBoolean(false);

	private final Map<String, SAPL> publishedDocuments = new HashMap<>();

	private final Map<String, DisjunctiveFormula> publishedTargets = new HashMap<>();

	private final Map<String, SAPL> unusableDocuments = new HashMap<>();

	public FastParsedDocumentIndex() {
		ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock(true);
		functionCtxReadLock = readWriteLock.readLock();
		functionCtxWriteLock = readWriteLock.writeLock();
	}

	public FastParsedDocumentIndex(FunctionContext functionCtx) {
		this();
		this.functionCtx = functionCtx;
	}

	@Override
	public void put(String documentKey, SAPL sapl) {
		Preconditions.checkArgument(documentKey != null && sapl != null);
		documentChanges.offer(Maps.immutableEntry(documentKey, sapl));
		if (documentSwitch.compareAndSet(true, false)) {
			updateDocumentReferences();
		}
	}

	@Override
	public void remove(String documentKey) {
		Preconditions.checkArgument(documentKey != null);
		documentChanges.offer(Maps.immutableEntry(documentKey, null));
		if (documentSwitch.compareAndSet(true, false)) {
			updateDocumentReferences();
		}
	}

	@Override
	public PolicyRetrievalResult retrievePolicies(Request request,
			FunctionContext functionCtx, Map<String, JsonNode> variables) {
		lazyInit(Preconditions.checkNotNull(functionCtx));
		PolicyRetrievalResult result;
		try {
			VariableContext variableCtx = new VariableContext(request, variables);
			result = documentIndex.match(functionCtx, variableCtx);
		}
		catch (PolicyEvaluationException e) {
			result = new PolicyRetrievalResult(Collections.emptyList(), true);
		}
		if (!unusableDocuments.isEmpty()) {
			return new PolicyRetrievalResult(result.getMatchingDocuments(), true);
		}
		return result;
	}

	@Override
	public void setLiveMode() {
		if (liveSwitch.compareAndSet(false, true)) {
			createDocumentIndex();
		}
	}

	@Override
	public void updateFunctionContext(FunctionContext functionCtx) {
		bufferCtx = Preconditions.checkNotNull(functionCtx);
		if (functionCtxSwitch.compareAndSet(true, false)) {
			updateFunctionContextReference();
		}
	}

	private void createDocumentIndex() {
		documentIndex = creationStrategy.construct(publishedDocuments, publishedTargets);
	}

	private void discard(String documentKey) {
		publishedDocuments.remove(documentKey);
		publishedTargets.remove(documentKey);
		unusableDocuments.remove(documentKey);
	}

	private void discardUnusables() {
		for (String documentKey : unusableDocuments.keySet()) {
			publishedDocuments.remove(documentKey);
			publishedTargets.remove(documentKey);
		}
	}

	private boolean hasFunctionCtx() {
		return functionCtx != null;
	}

	private void lazyInit(FunctionContext functionCtx) {
		Preconditions.checkState(liveSwitch.get());
		if (initSwitch.compareAndSet(true, false)) {
			functionCtxWriteLock.lock();
			try {
				if (!hasFunctionCtx()) {
					bufferCtx = functionCtx;
					updateFunctionContextReference();
				}
				updateDocumentReferences();
			}
			finally {
				functionCtxWriteLock.unlock();
				initLatch.countDown();
			}
		}
		try {
			initLatch.await();
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void processDocumentChanges() {
		if (hasFunctionCtx()) {
			while (!documentChanges.isEmpty()) {
				Map.Entry<String, SAPL> entry = documentChanges.poll();
				if (entry.getValue() != null) {
					retainDocument(entry.getKey(), entry.getValue());
					retainTarget(entry.getKey(), entry.getValue());
				}
				else {
					discard(entry.getKey());
				}
			}
			discardUnusables();
			if (liveSwitch.get()) {
				createDocumentIndex();
			}
		}
	}

	private void processFunctionContextChanges() {
		while (bufferCtx != functionCtx) {
			functionCtx = bufferCtx;
			publishedTargets.clear();
			publishedDocuments.putAll(unusableDocuments);
			unusableDocuments.clear();
			for (Map.Entry<String, SAPL> entry : publishedDocuments.entrySet()) {
				retainTarget(entry.getKey(), entry.getValue());
			}
			discardUnusables();
			if (liveSwitch.get()) {
				createDocumentIndex();
			}
		}
	}

	private void retainDocument(String documentKey, SAPL sapl) {
		publishedDocuments.put(documentKey, sapl);
	}

	private void retainTarget(String documentKey, SAPL sapl) {
		try {
			Expression targetExpression = sapl.getPolicyElement().getTargetExpression();
			DisjunctiveFormula targetFormula;
			if (targetExpression == null) {
				targetFormula = new DisjunctiveFormula(
						new ConjunctiveClause(new Literal(new Bool(true))));
			}
			else {
				Map<String, String> imports = sapl.fetchFunctionImports(functionCtx);
				targetFormula = TreeWalker.walk(targetExpression, imports);
			}
			publishedTargets.put(documentKey, targetFormula);
		}
		catch (PolicyEvaluationException e) {
			unusableDocuments.put(documentKey, sapl);
		}
	}

	private void updateDocumentReferences() {
		functionCtxReadLock.lock();
		try {
			processDocumentChanges();
			synchronized (documentChanges) {
				processDocumentChanges();
				documentSwitch.set(true);
			}
		}
		finally {
			functionCtxReadLock.unlock();
		}
	}

	private void updateFunctionContextReference() {
		functionCtxWriteLock.lock();
		try {
			processFunctionContextChanges();
			synchronized (bufferCtx) {
				processFunctionContextChanges();
				functionCtxSwitch.set(true);
			}
		}
		finally {
			functionCtxWriteLock.unlock();
		}
	}

}
