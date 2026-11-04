package com.campus.spaces

import android.content.Context
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeQuery
import com.amap.api.services.help.Inputtips
import com.amap.api.services.help.InputtipsQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class OriginPlace(val latitude: Double, val longitude: Double, val title: String, val address: String = "")

class PlaceRepository(context: Context) {
    private val context = context.applicationContext

    // 高德搜索结果已经是 GCJ-02；不再把未知坐标或系统 GPS 原样传入空间查询。
    suspend fun suggestions(keyword: String): List<OriginPlace> = withContext(Dispatchers.IO) {
        val query = InputtipsQuery(keyword.trim(), "").apply { cityLimit = false }
        Inputtips(context, query).requestInputtips().orEmpty().mapNotNull { tip ->
            val point = tip.point ?: return@mapNotNull null
            if (!validPoint(point.latitude, point.longitude)) return@mapNotNull null
            OriginPlace(point.latitude, point.longitude, tip.name.orEmpty(),
                listOfNotNull(tip.district, tip.address).filter { it.isNotBlank() }.distinct().joinToString(" "))
        }.distinctBy { Triple(it.title, it.latitude, it.longitude) }
    }

    suspend fun address(latitude: Double, longitude: Double): String = withContext(Dispatchers.IO) {
        GeocodeSearch(context).getFromLocation(
            RegeocodeQuery(LatLonPoint(latitude, longitude), 150f, GeocodeSearch.AMAP),
        )?.formatAddress.orEmpty()
    }
}

internal fun validPoint(latitude: Double, longitude: Double) =
    latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0
