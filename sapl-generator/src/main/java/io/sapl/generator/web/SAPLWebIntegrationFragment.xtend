/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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
package io.sapl.generator.web

import com.google.inject.Inject
import java.util.Collections
import java.util.regex.Pattern
import org.eclipse.xtext.xtext.generator.model.FileAccessFactory
import org.eclipse.xtext.xtext.generator.web.WebIntegrationFragment

import static extension org.eclipse.xtext.GrammarUtil.*
import java.util.ArrayList
import java.util.Set
import org.eclipse.xtext.xtext.generator.IXtextGeneratorLanguage
import org.eclipse.xtext.xtext.generator.model.project.IXtextProjectConfig
import java.util.HashSet

class SAPLWebIntegrationFragment extends WebIntegrationFragment {

	String highlightingModuleName
	String highlightingPath
	String keywordsFilter = '\\w+'
	ArrayList<String> wordKeywords = newArrayList
	ArrayList<String> nonWordKeywords = newArrayList
	Set<String> allKeywords
	IXtextGeneratorLanguage language
	IXtextProjectConfig projectConfig

	@Inject FileAccessFactory fileAccessFactory

	def getHighlightingModuleName() {
		return highlightingModuleName
	}

	override setHighlightingModuleName(String moduleName) {
		this.highlightingModuleName = moduleName
		super.setHighlightingModuleName(moduleName)
	}

	def getHighlightingPath() {
		return highlightingPath
	}

	override setHighlightingPath(String path) {
		this.highlightingPath = path
		super.setHighlightingPath(path)
	}

	def getKeywordsFilter() {
		return keywordsFilter
	}

	override setKeywordsFilter(String keywordsFilter) {
		this.keywordsFilter = keywordsFilter
		super.setKeywordsFilter(keywordsFilter)
	}

	def getWordKeywords() {

		return new ArrayList(wordKeywords);
	}

	def getNonWordKeywords() {
		return new ArrayList(nonWordKeywords);
	}

	def setAllKeywords(Set<String> allKeywords) {
		this.allKeywords = new HashSet(allKeywords);
	}

	def getAllKeywords() {
		if (this.allKeywords !== null)
			return new HashSet<String>(this.allKeywords)
		return grammar.allKeywords
	}

	override getLanguage() {
		if (this.language !== null)
			return this.language
		return super.language
	}

	def setLanguage(IXtextGeneratorLanguage language) {
		this.language = language
	}

	override protected getProjectConfig() {
		if (this.projectConfig !== null)
			return this.projectConfig
		super.getProjectConfig()
	}

	def setProjectConfig(IXtextProjectConfig projectConfig) {
		this.projectConfig = projectConfig
	}

	override protected generateJsHighlighting(String langId) {
		var framework = this.framework.get;

		// correction is only necessary for the codemirror editor
		if (framework != Framework.CODEMIRROR)
			super.generateJsHighlighting(langId)

		val allKeywords = this.getAllKeywords()
		this.wordKeywords = newArrayList
		this.nonWordKeywords = newArrayList
		val keywordsFilterPattern = Pattern.compile(keywordsFilter)
		val wordKeywordPattern = Pattern.compile('\\w(.*\\w)?')
		allKeywords.filter[keywordsFilterPattern.matcher(it).matches].forEach [
			if (wordKeywordPattern.matcher(it).matches)
				wordKeywords += it
			else if (it != '-') // dashes are part of the SAPL keywords so ignore it
				nonWordKeywords += it
		]

		// Sort descending to allow keywords with dashes to be highlighted
		Collections.sort(wordKeywords, Collections.reverseOrder)
		Collections.sort(nonWordKeywords, Collections.reverseOrder)

		if (fileAccessFactory !== null) {
			val jsFile = fileAccessFactory.createTextFile()
			jsFile.path = highlightingPath

			val patterns = createCodeMirrorPatterns(langId, allKeywords)

			if (!wordKeywords.empty)
				patterns.put('start', '''{token: "keyword", regex: «generateKeywordsRegExp»}''')
			if (!nonWordKeywords.empty)
				patterns.put('start', '''{token: "keyword", regex: «generateExtraKeywordsRegExp»}''')
			jsFile.content = '''
				define(«IF !highlightingModuleName.nullOrEmpty»"«highlightingModuleName»", «ENDIF»["codemirror", "codemirror/addon/mode/simple"], function(CodeMirror, SimpleMode) {
					«generateKeywords(wordKeywords, nonWordKeywords)»
					CodeMirror.defineSimpleMode("xtext/«langId»", {
						«FOR state : patterns.keySet SEPARATOR ','»
							«state»: «IF state == 'meta'»{«ELSE»[«ENDIF»
								«FOR rule : patterns.get(state) SEPARATOR ',\n'»«rule»«ENDFOR»
							«IF state == 'meta'»}«ELSE»]«ENDIF»
						«ENDFOR»
					});
				});
			'''

			jsFile.writeTo(this.getProjectConfig.web.assets)
		}
	}

	override generate() {
		// setup private fields from parent class so we have access to them
		if (highlightingModuleName !== null && highlightingModuleName.endsWith('.js'))
			highlightingModuleName = highlightingModuleName.substring(0, highlightingModuleName.length - 3)

		val langId = this.getLanguage.fileExtensions.head

		val hlModName = highlightingModuleName ?: switch framework.get {
			case ORION: 'xtext-resources/generated/' + langId + '-syntax'
			case ACE,
			case CODEMIRROR: 'xtext-resources/generated/mode-' + langId
		}

		if (this.generateJsHighlighting.get && this.getProjectConfig.web.assets !== null) {
			if (highlightingPath.nullOrEmpty)
				highlightingPath = hlModName + '.js'
		}

		super.generate()

	}

}
