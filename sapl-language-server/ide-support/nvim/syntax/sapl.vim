" Vim syntax file for SAPL (Streaming Attribute Policy Language)

if exists("b:current_syntax")
  finish
endif

" Keywords
syn keyword saplKeyword policy set for
syn keyword saplEntitlement permit deny
syn keyword saplClause where advice obligation transform on resource
syn keyword saplTarget action subject environment
syn keyword saplConditional if then else
syn keyword saplOperator var as in import schema each and or not

" Combining algorithms
syn keyword saplAlgorithm first-applicable only-one-applicable deny-overrides permit-overrides deny-unless-permit permit-unless-deny

" Constants
syn keyword saplConstant true false null undefined

" Strings
syn region saplString start='"' end='"' skip='\\"' contains=saplEscape
syn match saplEscape '\\.' contained

" Numbers
syn match saplNumber '\<-\?\d\+\(\.\d\+\)\?\([eE][+-]\?\d\+\)\?\>'

" Comments
syn match saplComment '//.*$'
syn region saplComment start='/\*' end='\*/'

" Attributes
syn match saplAttribute '<[a-zA-Z_][a-zA-Z0-9_.]*>'

" Functions
syn match saplFunction '\<[a-zA-Z_][a-zA-Z0-9_]*\(\.[a-zA-Z_][a-zA-Z0-9_]*\)*\s*('me=e-1

" Operators
syn match saplOperatorSymbol '==\|!=\|<=\|>=\|<\|>\|=\~'
syn match saplOperatorSymbol '&&\|||\|!'
syn match saplOperatorSymbol '|-\|||\|::\|:'

" Highlighting
hi def link saplKeyword Keyword
hi def link saplEntitlement Statement
hi def link saplClause Keyword
hi def link saplTarget Type
hi def link saplConditional Conditional
hi def link saplOperator Keyword
hi def link saplAlgorithm PreProc
hi def link saplConstant Constant
hi def link saplString String
hi def link saplEscape SpecialChar
hi def link saplNumber Number
hi def link saplComment Comment
hi def link saplAttribute Identifier
hi def link saplFunction Function
hi def link saplOperatorSymbol Operator

let b:current_syntax = "sapl"
