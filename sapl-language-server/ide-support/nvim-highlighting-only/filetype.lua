--[[
    SAPL Filetype Detection

    Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
    SPDX-License-Identifier: Apache-2.0

    Registers .sapl extension with Neovim's filetype system.
]]

vim.filetype.add({
  extension = {
    sapl = 'sapl',
  },
})
