#compdef sapl
# SPDX-License-Identifier: Apache-2.0
#
# SAPL CLI completion for zsh.
#
# picocli generates a bash completion script only. This file bridges that
# script into zsh through bashcompinit, zsh's bash-completion compatibility
# layer, so zsh users get the same completions without a separately maintained
# native zsh script. Installed as /usr/share/zsh/site-functions/_sapl.

if (( ! $+functions[bashcompinit] )); then
  autoload -U +X bashcompinit && bashcompinit
fi

if [[ -r /usr/share/bash-completion/completions/sapl ]]; then
  source /usr/share/bash-completion/completions/sapl
fi
