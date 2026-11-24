package com.mulocoolbot.gymtracker.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object AppIcons {

    val FlameIcon: ImageVector by lazy {
        ImageVector.Builder(
            name = "FlameIcon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.White),
                pathFillType = PathFillType.EvenOdd
            ) {
                moveTo(13.5f, 2.0f)
                curveTo(13.5f, 2.0f, 14.2f, 4.6f, 14.2f, 6.4f)
                curveTo(14.2f, 8.2f, 12.9f, 9.6f, 11.2f, 9.6f)
                curveTo(9.5f, 9.6f, 8.0f, 8.2f, 8.0f, 6.4f)
                lineTo(8.0f, 6.1f)
                curveTo(5.6f, 8.0f, 4.5f, 10.8f, 4.5f, 13.8f)
                curveTo(4.5f, 17.9f, 7.9f, 21.3f, 12.0f, 21.3f)
                curveTo(16.1f, 21.3f, 19.5f, 17.9f, 19.5f, 13.8f)
                curveTo(19.5f, 8.7f, 16.9f, 4.6f, 13.5f, 2.0f)
                close()
                moveTo(11.8f, 18.5f)
                curveTo(10.0f, 18.5f, 8.6f, 17.1f, 8.6f, 15.4f)
                curveTo(8.6f, 13.8f, 9.8f, 12.6f, 11.6f, 12.2f)
                curveTo(13.3f, 11.8f, 14.9f, 10.9f, 15.6f, 9.5f)
                curveTo(16.0f, 10.8f, 16.2f, 12.2f, 16.2f, 13.6f)
                curveTo(16.2f, 16.3f, 14.2f, 18.5f, 11.8f, 18.5f)
                close()
            }
        }.build()
    }

    val BoltIcon: ImageVector by lazy {
        ImageVector.Builder(
            name = "BoltIcon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.White),
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(13f, 2f)
                lineTo(4.5f, 13.5f)
                horizontalLineTo(11.5f)
                lineTo(10.5f, 22f)
                lineTo(19.5f, 10.5f)
                horizontalLineTo(12.5f)
                close()
            }
        }.build()
    }

    val ArrowRightIcon: ImageVector by lazy {
        ImageVector.Builder(
            name = "ArrowRightIcon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5f, 12f)
                horizontalLineTo(19f)
                moveTo(13f, 6f)
                lineTo(19f, 12f)
                lineTo(13f, 18f)
            }
        }.build()
    }

    val HomeIcon: ImageVector by lazy {
        ImageVector.Builder(
            name = "HomeIcon",
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
                moveTo(3.5f, 10.5f)
                lineTo(12f, 3.5f)
                lineTo(20.5f, 10.5f)
                verticalLineTo(19.5f)
                curveTo(20.5f, 20.33f, 19.83f, 21f, 19f, 21f)
                horizontalLineTo(5f)
                curveTo(4.17f, 21f, 3.5f, 20.33f, 3.5f, 19.5f)
                close()
                moveTo(9.5f, 21f)
                verticalLineTo(15f)
                curveTo(9.5f, 13.62f, 10.62f, 12.5f, 12f, 12.5f)
                curveTo(13.38f, 12.5f, 14.5f, 13.62f, 14.5f, 15f)
                verticalLineTo(21f)
            }
        }.build()
    }

    val CalendarPlanIcon: ImageVector by lazy {
        ImageVector.Builder(
            name = "CalendarPlanIcon",
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
                moveTo(5.5f, 4.5f)
                horizontalLineTo(18.5f)
                curveTo(19.6f, 4.5f, 20.5f, 5.4f, 20.5f, 6.5f)
                verticalLineTo(19f)
                curveTo(20.5f, 20.1f, 19.6f, 21f, 18.5f, 21f)
                horizontalLineTo(5.5f)
                curveTo(4.4f, 21f, 3.5f, 20.1f, 3.5f, 19f)
                verticalLineTo(6.5f)
                curveTo(3.5f, 5.4f, 4.4f, 4.5f, 5.5f, 4.5f)
                close()
                moveTo(8f, 2.5f)
                verticalLineTo(6.5f)
                moveTo(16f, 2.5f)
                verticalLineTo(6.5f)
                moveTo(3.5f, 9.5f)
                horizontalLineTo(20.5f)
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

    val StatsBarIcon: ImageVector by lazy {
        ImageVector.Builder(
            name = "StatsBarIcon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.4f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(6f, 20f)
                verticalLineTo(14f)
                moveTo(12f, 20f)
                verticalLineTo(4f)
                moveTo(18f, 20f)
                verticalLineTo(10f)
            }
        }.build()
    }

    val SettingsSlidersIcon: ImageVector by lazy {
        ImageVector.Builder(
            name = "SettingsSlidersIcon",
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
                moveTo(3.5f, 6f)
                horizontalLineTo(6.8f)
                moveTo(11.2f, 6f)
                horizontalLineTo(20.5f)
                moveTo(11.2f, 6f)
                arcToRelative(2.2f, 2.2f, 0f, isMoreThanHalf = true, isPositiveArc = true, -4.4f, 0f)
                arcToRelative(2.2f, 2.2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 4.4f, 0f)
                close()
                moveTo(3.5f, 12f)
                horizontalLineTo(12.8f)
                moveTo(17.2f, 12f)
                horizontalLineTo(20.5f)
                moveTo(17.2f, 12f)
                arcToRelative(2.2f, 2.2f, 0f, isMoreThanHalf = true, isPositiveArc = true, -4.4f, 0f)
                arcToRelative(2.2f, 2.2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 4.4f, 0f)
                close()
                moveTo(3.5f, 18f)
                horizontalLineTo(7.8f)
                moveTo(12.2f, 18f)
                horizontalLineTo(20.5f)
                moveTo(12.2f, 18f)
                arcToRelative(2.2f, 2.2f, 0f, isMoreThanHalf = true, isPositiveArc = true, -4.4f, 0f)
                arcToRelative(2.2f, 2.2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 4.4f, 0f)
                close()
            }
        }.build()
    }

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
}

val FlameIcon: ImageVector
    get() = AppIcons.FlameIcon

val BoltIcon: ImageVector
    get() = AppIcons.BoltIcon

val ArrowRightIcon: ImageVector
    get() = AppIcons.ArrowRightIcon

val HomeIcon: ImageVector
    get() = AppIcons.HomeIcon

val CalendarPlanIcon: ImageVector
    get() = AppIcons.CalendarPlanIcon

val DumbbellIcon: ImageVector
    get() = AppIcons.DumbbellIcon

val StatsBarIcon: ImageVector
    get() = AppIcons.StatsBarIcon

val SettingsSlidersIcon: ImageVector
    get() = AppIcons.SettingsSlidersIcon

val SdCardSaveIcon: ImageVector
    get() = AppIcons.SdCardSaveIcon

val GoogleLogoIcon: ImageVector
    get() = AppIcons.GoogleLogoIcon
