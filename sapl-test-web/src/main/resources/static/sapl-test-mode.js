define("sapl-test-mode", ["codemirror", "codemirror/addon/mode/simple"], function(CodeMirror, SimpleMode) {
	var keywords = "with|where|when|wait|virtualTime|variable|value|undefined|true|then|text|tests|subject|starts|single|scenario|returns|returning|return|resource|register|permit-unless-deny|permit-overrides|permit|pdp|parent|parameters|only-one-applicable|once|on|obligations|obligation|object|number|null|notApplicable|matching|library|let|key|is|invoked|integration|indeterminate|given|function|for|false|expect|exception|error|equals|environment|ends|empty|deny-unless-permit|deny-overrides|deny|decision|custom|contains|containing|config|boolean|blank|attribute|attempts|array|any|and|advice|action|TemporalFunctionLibrary|StandardFunctionLibrary|PIP|LoggingFunctionLibrary|FilterFunctionLibrary";
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
