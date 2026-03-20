/*
 * Copyright (C) 2025 O﻿ute﻿rTu﻿ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.vynce.app.utils.scanners

import com.vynce.app.models.SongTempData
import java.io.File

class TagLibScanner : MetadataScanner {
    override suspend fun getAllMetadataFromFile(file: File): SongTempData {
        throw UnsupportedOperationException("TagLib scanner is unavailable because native submodules were not initialized")
    }
}
