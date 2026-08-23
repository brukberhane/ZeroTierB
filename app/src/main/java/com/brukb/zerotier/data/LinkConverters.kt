package com.brukb.zerotier.data

import androidx.room.TypeConverter
import com.brukb.zerotier.data.model.LinkKind
import com.brukb.zerotier.data.model.LinkMode

class LinkConverters {
    @TypeConverter
    fun fromLinkKind(value: LinkKind): String = value.name

    @TypeConverter
    fun toLinkKind(value: String): LinkKind =
        LinkKind.entries.firstOrNull { it.name == value } ?: LinkKind.OTHER

    @TypeConverter
    fun fromLinkMode(value: LinkMode): String = value.name

    @TypeConverter
    fun toLinkMode(value: String): LinkMode =
        LinkMode.entries.firstOrNull { it.name == value } ?: LinkMode.PROXY
}
