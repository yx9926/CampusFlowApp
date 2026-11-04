package com.campus.spaces

import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.TextureMapView
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MarkerOptions

@Composable
internal fun OriginMap(
    selected: OriginPlace?,
    camera: OriginCamera?,
    onPick: (OriginPlace) -> Unit,
    onLoaded: () -> Unit,
    onFailure: () -> Unit,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val pick by rememberUpdatedState(onPick)
    val loaded by rememberUpdatedState(onLoaded)
    val failed by rememberUpdatedState(onFailure)
    val mapView = remember {
        runCatching { TextureMapView(context).apply { onCreate(Bundle()) } }.getOrElse {
            onFailure()
            null
        }
    }
    if (mapView == null) return

    DisposableEffect(mapView, owner) {
        val map = mapView.map
        map.uiSettings.isZoomControlsEnabled = false
        map.setOnMapLoadedListener { loaded() }
        map.setOnMapClickListener { point -> pick(OriginPlace(point.latitude, point.longitude, "地图选点")) }
        map.setOnPOIClickListener { poi -> pick(OriginPlace(poi.coordinate.latitude, poi.coordinate.longitude, poi.name)) }
        // Compose 弹窗移除时释放地图；前后台切换同步到原生 MapView。
        var resumed = false
        fun resume() { if (!resumed) { mapView.onResume(); resumed = true } }
        fun pause() { if (resumed) { mapView.onPause(); resumed = false } }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resume()
                Lifecycle.Event.ON_PAUSE -> pause()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resume()
        onDispose {
            owner.lifecycle.removeObserver(observer)
            map.setOnMapClickListener(null)
            map.setOnPOIClickListener(null)
            map.setOnMapLoadedListener(null)
            pause()
            mapView.onDestroy()
        }
    }
    LaunchedEffect(selected?.latitude, selected?.longitude) {
        selected?.let {
            runCatching {
                mapView.map.clear()
                val point = LatLng(it.latitude, it.longitude)
                mapView.map.addMarker(MarkerOptions().position(point).title(it.title))
            }.onFailure { failed() }
        }
    }
    LaunchedEffect(camera) {
        if (camera != null) {
            runCatching {
                mapView.map.moveCamera(CameraUpdateFactory.newLatLngZoom(camera.point, camera.zoom))
            }.onFailure { failed() }
        }
    }
    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
}
