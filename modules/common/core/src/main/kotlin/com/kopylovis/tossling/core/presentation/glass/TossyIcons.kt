package com.kopylovis.tossling.core.presentation.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

object TossyIcons {

    val Back: ImageVector by lazy { stroked(name = "back", viewport = 20f, width = 2f, "M12.5 4l-6 6 6 6") }

    val More: ImageVector by lazy {
        filled(
            name = "more",
            viewport = 20f,
            circle(4f, 10f, 1.8f) + circle(10f, 10f, 1.8f) + circle(16f, 10f, 1.8f),
        )
    }

    val Laptop: ImageVector by lazy {
        stroked(name = "laptop", viewport = 24f, width = 1.8f, rect(5f, 6f, 14f, 10f, 1.5f), "M3 18.5h18")
    }

    val Phone: ImageVector by lazy {
        stroked(name = "phone", viewport = 24f, width = 1.7f, rect(7f, 3f, 10f, 18f, 2.5f), "M11 18h2")
    }

    val Copy: ImageVector by lazy {
        stroked(
            name = "copy",
            viewport = 18f,
            width = 1.6f,
            rect(6f, 6f, 9f, 9f, 2f),
            "M12 6V4.5A1.5 1.5 0 0 0 10.5 3h-6A1.5 1.5 0 0 0 3 4.5v6A1.5 1.5 0 0 0 4.5 12H6",
        )
    }

    val Alert: ImageVector by lazy {
        stroked(name = "alert", viewport = 24f, width = 2f, circle(12f, 12f, 9f), "M12 7.5v5.5", "M12 16.5v.01")
    }

    val Qr: ImageVector by lazy {
        stroked(
            name = "qr",
            viewport = 20f,
            width = 1.7f,
            rect(3f, 3f, 5f, 5f, 1f),
            rect(12f, 3f, 5f, 5f, 1f),
            rect(3f, 12f, 5f, 5f, 1f),
            "M12 12h2v2h-2zM15 15h2v2h-2z",
        )
    }

    val Tile: ImageVector by lazy {
        stroked(
            name = "tile",
            viewport = 20f,
            width = 1.6f,
            rect(3f, 3f, 6f, 6f, 1.5f),
            rect(11f, 3f, 6f, 6f, 1.5f),
            rect(3f, 11f, 6f, 6f, 1.5f),
            rect(11f, 11f, 6f, 6f, 3f),
        )
    }

    val Close: ImageVector by lazy { stroked(name = "close", viewport = 14f, width = 1.8f, "M2 2l10 10M12 2L2 12") }

    val ArrowDown: ImageVector by lazy { stroked(name = "arrow_down", viewport = 14f, width = 1.8f, "M7 2v10M3 8l4 4 4-4") }

    val ArrowUp: ImageVector by lazy { stroked(name = "arrow_up", viewport = 14f, width = 1.8f, "M7 12V2M3 6l4-4 4 4") }

    val Clipboard: ImageVector by lazy {
        stroked(name = "clipboard", viewport = 24f, width = 1.7f, rect(6f, 5f, 12f, 15f, 2.5f), "M9.5 3.5h5v3h-5z")
    }

    val Sliders: ImageVector by lazy {
        stroked(
            name = "sliders",
            viewport = 22f,
            width = 1.7f,
            "M3 7h10M17 7h2M3 15h2M9 15h10",
            circle(15f, 7f, 2f),
            circle(7f, 15f, 2f),
        )
    }

    val Refresh: ImageVector by lazy {
        stroked(name = "refresh", viewport = 20f, width = 1.7f, "M16 10a6 6 0 1 1-1.8-4.3", "M16 3.5v3h-3")
    }

    val Unlink: ImageVector by lazy {
        stroked(
            name = "unlink",
            viewport = 20f,
            width = 1.7f,
            "M8 12l-2 2a2.8 2.8 0 0 1-4-4l2-2M12 8l2-2a2.8 2.8 0 0 1 4 4l-2 2M3 3l14 14",
        )
    }

    val KeyOff: ImageVector by lazy {
        stroked(name = "key_off", viewport = 24f, width = 1.8f, circle(8f, 12f, 3.5f), "M11.5 12H20M17 12v3M20 12v2", "M3 3l18 18")
    }

    val ChevronRight: ImageVector by lazy { stroked(name = "chevron_right", viewport = 16f, width = 1.8f, "M6 3l5 5-5 5") }

    val Check: ImageVector by lazy { stroked(name = "check", viewport = 14f, width = 2f, "M2.5 7.5l3 3 6-7") }

    val Bell: ImageVector by lazy {
        stroked(name = "bell", viewport = 24f, width = 1.8f, "M6 16v-5a6 6 0 0 1 12 0v5l1.5 2h-15z", "M10 20.5a2 2 0 0 0 4 0")
    }

    val Trash: ImageVector by lazy {
        stroked(name = "trash", viewport = 20f, width = 1.8f, "M4 6h12M8 6V4h4v2M6 6l1 10h6l1-10")
    }

    val Pin: ImageVector by lazy {
        stroked(name = "pin", viewport = 20f, width = 1.8f, "M12 3l5 5M13.5 4.5l-4 4-4 .5 5.5 5.5.5-4 4-4M8.2 11.8L3.5 16.5")
    }

    val Search: ImageVector by lazy {
        stroked(name = "search", viewport = 20f, width = 1.8f, "M9 3.5a5.5 5.5 0 1 0 0 11a5.5 5.5 0 1 0 0-11zM13 13l4 4")
    }

    val Done: ImageVector by lazy { stroked(name = "done", viewport = 20f, width = 1.8f, "M4 10.5l4 4 8-9") }

    val DoneAll: ImageVector by lazy { stroked(name = "done_all", viewport = 20f, width = 1.8f, "M2 10.5l3.5 3.5 7-8M9 13.5l.5.5 8-9") }

    val Projects: ImageVector by lazy {
        stroked(
            name = "projects",
            viewport = 20f,
            width = 1.7f,
            rect(3f, 3f, 6f, 6f, 3f),
            rect(11f, 3f, 6f, 6f, 3f),
            rect(3f, 11f, 6f, 6f, 3f),
            rect(11f, 11f, 6f, 6f, 3f),
        )
    }

    val External: ImageVector by lazy { stroked(name = "external", viewport = 14f, width = 1.8f, "M5 2h7v7M12 2L3 11") }

    val Devices: ImageVector by lazy {
        stroked(name = "devices", viewport = 24f, width = 1.7f, rect(2.5f, 6f, 12f, 9f, 1.5f), "M1 18h15", rect(16.5f, 8f, 6f, 11f, 1.5f))
    }

    val Document: ImageVector by lazy {
        stroked(name = "document", viewport = 24f, width = 1.7f, "M7 3h7l5 5v11.5a1.5 1.5 0 0 1-1.5 1.5h-10.5a1.5 1.5 0 0 1-1.5-1.5v-15a1.5 1.5 0 0 1 1.5-1.5z", "M14 3v5h5")
    }

    val Plus: ImageVector by lazy { stroked(name = "plus", viewport = 18f, width = 2f, "M9 3v12M3 9h12") }

    private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float): String =
        "M${x + r},${y}h${w - 2 * r}a$r,$r 0 0 1 $r,${r}v${h - 2 * r}a$r,$r 0 0 1 -$r,${r}" +
            "h${-(w - 2 * r)}a$r,$r 0 0 1 -$r,-${r}v${-(h - 2 * r)}a$r,$r 0 0 1 $r,-${r}z"

    private fun circle(cx: Float, cy: Float, r: Float): String =
        "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0z"

    private fun stroked(name: String, viewport: Float, width: Float, vararg paths: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = viewport.dp,
            defaultHeight = viewport.dp,
            viewportWidth = viewport,
            viewportHeight = viewport,
        ).apply {
            paths.forEach { data ->
                addPath(
                    pathData = PathParser().parsePathString(data).toNodes(),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private fun filled(name: String, viewport: Float, data: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = viewport.dp,
            defaultHeight = viewport.dp,
            viewportWidth = viewport,
            viewportHeight = viewport,
        ).apply {
            addPath(pathData = PathParser().parsePathString(data).toNodes(), fill = SolidColor(Color.Black))
        }.build()
}
