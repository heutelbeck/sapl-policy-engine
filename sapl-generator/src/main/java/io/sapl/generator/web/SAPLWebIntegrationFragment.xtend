package io.sapl.generator.web

import com.google.inject.Inject
import java.util.Collections
import java.util.regex.Pattern
import org.eclipse.xtext.xtext.generator.model.FileAccessFactory
import org.eclipse.xtext.xtext.generator.web.WebIntegrationFragment

import static extension org.eclipse.xtext.GrammarUtil.*

class SAPLWebIntegrationFragment extends WebIntegrationFragment {

	String highlightingModuleName
	String highlightingPath
	String keywordsFilter = '\\w+'

	@Inject FileAccessFactory fileAccessFactory

	override setHighlightingModuleName(String moduleName) {
		this.highlightingModuleName = moduleName
		super.setHighlightingModuleName(moduleName)
	}
	
	override setHighlightingPath(String path) {
		this.highlightingPath = path
		super.setHighlightingPath(path)
	}
	
	override setKeywordsFilter(String keywordsFilter) {
		this.keywordsFilter = keywordsFilter
		super.setKeywordsFilter(keywordsFilter)
	}

	override protected generateJsHighlighting(String langId) {
		var framework = this.framework.get;
		
		// correction is only necessary for the codemirror editor
		if(framework != Framework.CODEMIRROR)
			super.generateJsHighlighting(langId)
			
		val allKeywords = grammar.allKeywords
		val wordKeywords = newArrayList
		val nonWordKeywords = newArrayList
		val keywordsFilterPattern = Pattern.compile(keywordsFilter)
		val wordKeywordPattern = Pattern.compile('\\w(.*\\w)?')
		allKeywords.filter[keywordsFilterPattern.matcher(it).matches].forEach[
			if (wordKeywordPattern.matcher(it).matches)
				wordKeywords += it
			else if (it != '-') // dashes are part of the SAPL keywords so ignore it
				nonWordKeywords += it
		]
		
		// Sort descending to allow keywords with dashes to be highlighted
		Collections.sort(wordKeywords, Collections.reverseOrder)
		Collections.sort(nonWordKeywords, Collections.reverseOrder)
			
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
		
		jsFile.writeTo(projectConfig.web.assets)
	}
	
	override generate() {
		// setup private fields from parent class so we have access to them
		if (highlightingModuleName !== null && highlightingModuleName.endsWith('.js'))
			highlightingModuleName = highlightingModuleName.substring(0, highlightingModuleName.length - 3)
			
		val langId = language.fileExtensions.head
		
		val hlModName = highlightingModuleName ?: switch framework.get {
			case ORION: 'xtext-resources/generated/' + langId + '-syntax'
			case ACE, case CODEMIRROR: 'xtext-resources/generated/mode-' + langId
		}
		
		if (generateJsHighlighting.get && projectConfig.web.assets !== null) {
			if (highlightingPath.nullOrEmpty)
				highlightingPath = hlModName + '.js'
		}
		
		super.generate()
		
	}

}