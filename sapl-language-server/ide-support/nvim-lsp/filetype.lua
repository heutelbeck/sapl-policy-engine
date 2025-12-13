--[[
    SAPL Language Server - Filetype Detection

    Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
    SPDX-License-Identifier: Apache-2.0

    Registers .sapl extension with Neovim's filetype system.
    Merge into your existing filetype.lua or add to ~/.config/nvim/.
]]

-- vim.filetype.add() is the modern approach (Neovim 0.8+).
-- For older versions, use ftdetect/*.vim or autocmd.
vim.filetype.add({
  extension = {
    sapl = 'sapl',
  },
})
