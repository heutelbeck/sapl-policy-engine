" Vim syntax file for SAPL (Streaming Attribute Policy Language)
"
" Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
" SPDX-License-Identifier: Apache-2.0

if exists("b:current_syntax")
  finish
endif

" Keywords
syn keyword saplKeyword policy set for where var as import schema enforced each

" Entitlements
syn keyword saplEntitlement permit deny

" Combining algorithms (use match for hyphenated words)
syn match saplAlgorithm '\<first-applicable\>'
syn match saplAlgorithm '\<only-one-applicable\>'
syn match saplAlgorithm '\<deny-overrides\>'
syn match saplAlgorithm '\<permit-overrides\>'
syn match saplAlgorithm '\<deny-unless-permit\>'
syn match saplAlgorithm '\<permit-unless-deny\>'

" Clauses
syn keyword saplClause advice obligation transform

" Authorization subscription variables
syn keyword saplAuthzVar subject action resource environment

" Operators as keywords
syn keyword saplKeywordOp in

" Constants
syn keyword saplConstant true false null undefined

" Strings (including escaped keys and object keys)
syn region saplString start='"' end='"' skip='\\"' contains=saplEscape
syn match saplEscape '\\.' contained

" Numbers (JSON-style: integers, decimals, scientific notation)
syn match saplNumber '\<-\?\d\+\(\.\d\+\)\?\([eE][+-]\?\d\+\)\?\>'

" Comments
syn match saplComment '//.*$'
syn region saplComment start='/\*' end='\*/'

" Attribute finders: <name>, |<name>
" Must come before operators to take precedence
syn match saplAttribute '|<[a-zA-Z_][a-zA-Z0-9_.]*\(([^)]*)\)\?\(\[[^\]]*\]\)\?>'
syn match saplAttribute '<[a-zA-Z_][a-zA-Z0-9_.]*\(([^)]*)\)\?\(\[[^\]]*\]\)\?>'

" Functions (identifier followed by parenthesis)
syn match saplFunction '\<[a-zA-Z_][a-zA-Z0-9_]*\(\.[a-zA-Z_][a-zA-Z0-9_]*\)*\s*('me=e-1

" Relative accessor
syn match saplRelative '@'

" Filter and subtemplate operators
syn match saplFilterOp '|-'
syn match saplSubtemplateOp '::'

" Comparison and equality operators
syn match saplOperatorSymbol '==\|!=\|<=\|>=\|=\~'

" Logical operators
syn match saplOperatorSymbol '&&\|||\|!'

" Arithmetic and bitwise operators
syn match saplOperatorSymbol '[+\-*/%^&|]'

" Structural symbols (brackets, braces, etc.)
syn match saplStructural '[\[\]{}().,;:]'

" Define SAPL-specific colors
hi saplKeyword        guifg=#CC7832 ctermfg=172
hi saplKeywordOp      guifg=#CC7832 ctermfg=172
hi saplEntitlement    guifg=#629755 ctermfg=65  gui=bold cterm=bold
hi saplAlgorithm      guifg=#629755 ctermfg=65  gui=bold cterm=bold
hi saplClause         guifg=#CC7832 ctermfg=172
hi saplAuthzVar       guifg=#6897BB ctermfg=67  gui=italic cterm=italic
hi saplConstant       guifg=#CC7832 ctermfg=172
hi saplString         guifg=#6A8759 ctermfg=65
hi saplEscape         guifg=#CC7832 ctermfg=172
hi saplNumber         guifg=#6897BB ctermfg=67
hi saplComment        guifg=#808080 ctermfg=244 gui=italic cterm=italic
hi saplAttribute      guifg=#299999 ctermfg=30
hi saplFunction       guifg=#FFC66D ctermfg=221
hi saplRelative       guifg=#5c6370 ctermfg=59
hi saplFilterOp       guifg=#5c6370 ctermfg=59
hi saplSubtemplateOp  guifg=#5c6370 ctermfg=59
hi saplOperatorSymbol guifg=#5c6370 ctermfg=59
hi saplStructural     guifg=#5c6370 ctermfg=59

let b:current_syntax = "sapl"
