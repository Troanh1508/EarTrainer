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

/** Rising trend arrow (Material "trending_up"), for a score above average. */
val Icons.Filled.TrendingUp: ImageVector by lazy {
    materialIcon(name = "Filled.TrendingUp", autoMirror = true) {
        materialPath {
            moveTo(16f, 6f)
            lineToRelative(2.29f, 2.29f)
            lineToRelative(-4.88f, 4.88f)
            lineToRelative(-4f, -4f)
            lineTo(2f, 16.59f)
            lineTo(3.41f, 18f)
            lineToRelative(6f, -6f)
            lineToRelative(4f, 4f)
            lineToRelative(6.3f, -6.29f)
            lineTo(22f, 12f)
            verticalLineTo(6f)
            close()
        }
    }
}

/** Falling trend arrow (Material "trending_down"), for a score below average. */
val Icons.Filled.TrendingDown: ImageVector by lazy {
    materialIcon(name = "Filled.TrendingDown", autoMirror = true) {
        materialPath {
            moveTo(16f, 18f)
            lineToRelative(2.29f, -2.29f)
            lineToRelative(-4.88f, -4.88f)
            lineToRelative(-4f, 4f)
            lineTo(2f, 7.41f)
            lineTo(3.41f, 6f)
            lineToRelative(6f, 6f)
            lineToRelative(4f, -4f)
            lineToRelative(6.3f, 6.29f)
            lineTo(22f, 12f)
            verticalLineToRelative(6f)
            close()
        }
    }
}

/** Level trend arrow (Material "trending_flat"), for a score equal to the average. */
val Icons.Filled.TrendingFlat: ImageVector by lazy {
    materialIcon(name = "Filled.TrendingFlat", autoMirror = true) {
        materialPath {
            moveTo(22f, 12f)
            lineToRelative(-4f, -4f)
            verticalLineToRelative(3f)
            horizontalLineTo(3f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(15f)
            verticalLineToRelative(3f)
            close()
        }
    }
}
