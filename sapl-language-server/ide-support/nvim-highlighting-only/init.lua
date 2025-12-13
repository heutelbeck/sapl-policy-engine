--[[
    SAPL Syntax Highlighting Only - Neovim Configuration

    Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
    SPDX-License-Identifier: Apache-2.0

    Minimal configuration for SAPL syntax highlighting without LSP.
    For full IDE features (completion, diagnostics, hover), use nvim-lsp/ instead.
]]

-- Register .sapl filetype
vim.filetype.add({
  extension = {
    sapl = 'sapl',
  },
})

-- Basic editor settings
vim.opt.number = true
vim.opt.tabstop = 4
vim.opt.shiftwidth = 4
vim.opt.expandtab = true
vim.opt.termguicolors = true
