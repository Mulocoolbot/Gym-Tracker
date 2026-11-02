package com.mulocoolbot.gymtracker.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object AppIcons {

    val SdCardSaveIcon: ImageVector by lazy {
        ImageVector.Builder(
            name = "SdCardSaveIcon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(18f, 3f)
                horizontalLineTo(9.5f)
                lineTo(5f, 7.5f)
                verticalLineTo(19f)
                curveTo(5f, 20.1f, 5.9f, 21f, 7f, 21f)
                horizontalLineTo(18f)
                curveTo(19.1f, 21f, 20f, 20.1f, 20f, 19f)
                verticalLineTo(5f)
                curveTo(20f, 3.9f, 19.1f, 3f, 18f, 3f)
                close()
            }
            path(
                fill = SolidColor(Color.White)
            ) {
                moveTo(10f, 5.5f)
                horizontalLineTo(11.5f)
                verticalLineTo(9.5f)
                horizontalLineTo(10f)
                close()
                moveTo(13f, 5.5f)
                horizontalLineTo(14.5f)
                verticalLineTo(9.5f)
                horizontalLineTo(13f)
                close()
                moveTo(16f, 5.5f)
                horizontalLineTo(17.5f)
                verticalLineTo(9.5f)
                horizontalLineTo(16f)
                close()
            }
        }.build()
    }

    val GoogleLogoIcon: ImageVector by lazy {
        ImageVector.Builder(
            name = "GoogleLogoIcon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFF4285F4))) {
                moveTo(21.35f, 11.1f)
                horizontalLineTo(12f)
                verticalLineTo(13.9f)
                horizontalLineTo(17.6f)
                curveTo(17.1f, 16.3f, 15.0f, 17.9f, 12f, 17.9f)
                verticalLineTo(21f)
                curveTo(17.2f, 21f, 21.5f, 16.8f, 21.5f, 12f)
                curveTo(21.5f, 11.7f, 21.45f, 11.4f, 21.35f, 11.1f)
                close()
            }
            path(fill = SolidColor(Color(0xFF34A853))) {
                moveTo(12f, 21f)
                curveTo(9.1f, 21f, 6.5f, 19.4f, 5.2f, 16.8f)
                lineTo(7.8f, 14.8f)
                curveTo(8.6f, 16.6f, 10.2f, 17.9f, 12f, 17.9f)
                verticalLineTo(21f)
                close()
            }
            path(fill = SolidColor(Color(0xFFFBBC05))) {
                moveTo(5.2f, 16.8f)
                curveTo(4.7f, 15.3f, 4.7f, 8.7f, 5.2f, 7.2f)
                lineTo(7.8f, 9.2f)
                curveTo(7.4f, 10.8f, 7.4f, 13.2f, 7.8f, 14.8f)
                lineTo(5.2f, 16.8f)
                close()
            }
            path(fill = SolidColor(Color(0xFFEA4335))) {
                moveTo(12f, 6.1f)
                curveTo(13.6f, 6.1f, 15.0f, 6.7f, 16.1f, 7.7f)
                lineTo(18.3f, 5.5f)
                curveTo(16.7f, 3.9f, 14.5f, 3f, 12f, 3f)
                curveTo(9.1f, 3f, 6.5f, 4.6f, 5.2f, 7.2f)
                lineTo(7.8f, 9.2f)
                curveTo(8.6f, 7.4f, 10.2f, 6.1f, 12f, 6.1f)
                close()
            }
        }.build()
    }

    val DumbbellIcon: ImageVector by lazy {
        ImageVector.Builder(
            name = "DumbbellIcon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.White)
            ) {
                moveTo(2f, 9f)
                horizontalLineTo(4f)
                verticalLineTo(15f)
                horizontalLineTo(2f)
                close()
                moveTo(5f, 6f)
                horizontalLineTo(7.5f)
                verticalLineTo(18f)
                horizontalLineTo(5f)
                close()
                moveTo(7.5f, 11f)
                horizontalLineTo(16.5f)
                verticalLineTo(13f)
                horizontalLineTo(7.5f)
                close()
                moveTo(16.5f, 6f)
                horizontalLineTo(19f)
                verticalLineTo(18f)
                horizontalLineTo(16.5f)
                close()
                moveTo(20f, 9f)
                horizontalLineTo(22f)
                verticalLineTo(15f)
                horizontalLineTo(20f)
                close()
            }
        }.build()
    }
}

val SdCardSaveIcon: ImageVector
    get() = AppIcons.SdCardSaveIcon

val GoogleLogoIcon: ImageVector
    get() = AppIcons.GoogleLogoIcon

val DumbbellIcon: ImageVector
    get() = AppIcons.DumbbellIcon

