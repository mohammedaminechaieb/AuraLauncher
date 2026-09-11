package com.auralauncher.app.data

import androidx.room.TypeConverter

class LauncherConverters {
    @TypeConverter
    fun fromPackageList(value: List<String>): String = value.joinToString(",")

    @TypeConverter
    fun toPackageList(value: String): List<String> =
        if (value.isBlank()) emptyList() else value.split(",")

    @TypeConverter
    fun fromShape(shape: IconShape): String = shape.name

    @TypeConverter
    fun toShape(value: String): IconShape = runCatching { IconShape.valueOf(value) }.getOrDefault(IconShape.SYSTEM_DEFAULT)
}
