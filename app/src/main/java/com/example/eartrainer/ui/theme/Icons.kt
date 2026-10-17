package com.example.eartrainer.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

/** Stop glyph; material-icons-core does not include one and the extended set is very large. */
val Icons.Filled.Stop: ImageVector by lazy {
    materialIcon(name = "Filled.Stop") {
        materialPath {
            moveTo(6f, 6f)
            horizontalLineToRelative(12f)
            verticalLineToRelative(12f)
            horizontalLineTo(6f)
            close()
        }
    }
}
