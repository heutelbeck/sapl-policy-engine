-- SAPL Language Server configuration for nvim-lspconfig

local lspconfig = require('lspconfig')
local configs = require('lspconfig.configs')

-- Register SAPL as a custom language server
if not configs.sapl then
  configs.sapl = {
    default_config = {
      -- UPDATE THIS PATH to point to your sapl-language-server.jar
      cmd = { 'java', '-jar', '/path/to/sapl-language-server-4.0.0-SNAPSHOT.jar' },
      filetypes = { 'sapl' },
      root_dir = function(fname)
        return vim.fn.fnamemodify(fname, ':h')
      end,
      settings = {},
    },
  }
end

lspconfig.sapl.setup({})
