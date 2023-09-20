define("sapl-test-mode", ["codemirror", "codemirror/addon/mode/simple"], function(CodeMirror, SimpleMode) {
	var keywords = "with|when|wait|virtualTime|variable|value|true|times|then|tests|subject|single|scenario|s|returns|returning|return|resource|permit|parent|parameters|once|on|obligations|obligation|notApplicable|matching|let|key|is|invoked|integration|indeterminate|given|functionLibrary|function|for|false|expect|exception|environment|deny|decision|attribute|attempts|any|and|advice|action|TemporalFunctionLibrary|StandardFunctionLibrary|PIP|LoggingFunctionLibrary|FilterFunctionLibrary";
	CodeMirror.defineSimpleMode("xtext/sapltest", {
		start: [
			{token: "comment", regex: "\\/\\/.*$"},
			{token: "comment", regex: "\\/\\*", next : "comment"},
			{token: "string", regex: '["](?:(?:\\\\.)|(?:[^"\\\\]))*?["]'},
			{token: "string", regex: "['](?:(?:\\\\.)|(?:[^'\\\\]))*?[']"},
			{token: "constant.numeric", regex: "[+-]?\\d+(?:(?:\\.\\d*)?(?:[eE][+-]?\\d+)?)?\\b"},
			{token: "keyword", regex: "\\b(?:" + keywords + ")\\b"}
		],
		comment: [
			{token: "comment", regex: ".*?\\*\\/", next : "start"},
			{token: "comment", regex: ".+"}
		],
		meta: {
			dontIndentStates: ["comment"],
			lineComment: "//"
		}
	});
});
