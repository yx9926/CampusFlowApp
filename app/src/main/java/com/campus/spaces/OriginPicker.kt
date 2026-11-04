package com.campus.spaces

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.amap.api.maps.MapsInitializer
import com.amap.api.location.AMapLocationClient
import com.amap.api.maps.model.LatLng
import com.amap.api.services.core.AMapException
import com.amap.api.services.core.ServiceSettings
import com.campus.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

@Composable
fun OriginPicker(initial: OriginPlace?, onDismiss: () -> Unit, onConfirm: (OriginPlace) -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("map_privacy", Context.MODE_PRIVATE) }
    var consent by remember { mutableStateOf(preferences.getBoolean("amap_v2", false)) }
    val uriHandler = LocalUriHandler.current
    if (!consent) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("使用高德地图") },
            text = {
                Column {
                    Text("地图、搜索与定位由高德提供，会处理搜索词、选点及设备和网络信息。授权定位后，App 会获取一次位置来显示附近地图。")
                    TextButton(onClick = { uriHandler.openUri("https://lbs.amap.com/pages/privacy/") }) {
                        Text("高德隐私政策")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    preferences.edit().putBoolean("amap_v2", true).apply()
                    consent = true
                }) { Text("同意并继续") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        )
        return
    }
    val configured = remember {
        val info = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        !info.metaData?.getString("com.amap.api.v2.apikey").isNullOrBlank()
    }
    val initialized = remember {
        runCatching {
            MapsInitializer.updatePrivacyShow(context, true, true)
            MapsInitializer.updatePrivacyAgree(context, true)
            MapsInitializer.setProtocol(MapsInitializer.HTTPS)
            AMapLocationClient.updatePrivacyShow(context, true, true)
            AMapLocationClient.updatePrivacyAgree(context, true)
            ServiceSettings.updatePrivacyShow(context, true, true)
            ServiceSettings.updatePrivacyAgree(context, true)
            ServiceSettings.getInstance().apply {
                protocol = ServiceSettings.HTTPS
                connectionTimeOut = 8_000
                soTimeOut = 8_000
            }
        }.isSuccess
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (!configured || !initialized) {
                Column(Modifier.safeDrawingPadding().padding(CampusSpacing.Page)) {
                    ErrorPanel(if (!configured) "未配置高德 Android Key" else "地图初始化失败", onDismiss)
                }
            } else OriginPickerContent(initial, onDismiss, onConfirm)
        }
    }
}

@Composable
private fun OriginPickerContent(initial: OriginPlace?, onDismiss: () -> Unit, onConfirm: (OriginPlace) -> Unit) {
    val context = LocalContext.current
    val repository = remember { PlaceRepository(context) }
    val focus = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable(stateSaver = listSaver<OriginPlace?, Any>(
        save = { place -> if (place == null) emptyList() else listOf(place.latitude, place.longitude, place.title, place.address) },
        restore = { values -> if (values.isEmpty()) null else OriginPlace(values[0] as Double, values[1] as Double, values[2] as String, values[3] as String) },
    )) { mutableStateOf(initial) }
    var suggestions by remember { mutableStateOf(emptyList<OriginPlace>()) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var searchRetry by remember { mutableIntStateOf(0) }
    var addressError by remember { mutableStateOf(false) }
    var addressRetry by remember { mutableIntStateOf(0) }
    var mapRetry by remember { mutableIntStateOf(0) }
    var mapLoaded by remember { mutableStateOf(false) }
    var mapError by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf(selected?.let { OriginCamera(LatLng(it.latitude, it.longitude), 16f) }) }
    var locate by remember { mutableStateOf(false) }
    var awaitingPermission by remember { mutableStateOf(true) }
    var locationMessage by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        awaitingPermission = false
        locate = context.hasOriginLocationPermission()
        locationMessage = if (locate) null else "未授权定位，可搜索地点"
    }
    fun requestLocation() {
        locationMessage = null
        awaitingPermission = !context.hasOriginLocationPermission()
        if (awaitingPermission) permission.launch(originLocationPermissions)
        else locate = true
    }
    LaunchedEffect(Unit) { requestLocation() }
    LaunchedEffect(locate) {
        if (!locate) return@LaunchedEffect
        val position = try {
            currentOriginCamera(context)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (!locate) return@LaunchedEffect
        if (position != null) camera = position
        else locationMessage = "暂时无法定位，可重试或搜索地点"
        locate = false
    }

    // 新输入或退出界面都会取消协程；旧搜索响应不能覆盖当前关键词的结果。
    LaunchedEffect(query, searchRetry) {
        suggestions = emptyList()
        searchError = null
        searching = query.isNotBlank()
        if (query.isBlank()) return@LaunchedEffect
        try {
            delay(350)
            suggestions = repository.suggestions(query)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            searchError = if (failure is AMapException) {
                when (failure.errorCode) {
                    1002, 1008, 1009 -> "地址搜索失败：高德授权未通过（${failure.errorCode}）"
                    else -> "地址搜索失败（${failure.errorCode}）"
                }
            } else "地址搜索失败，请检查网络"
        } finally {
            if (currentCoroutineContext().isActive) searching = false
        }
    }
    LaunchedEffect(selected?.latitude, selected?.longitude, addressRetry) {
        addressError = false
        val place = selected ?: return@LaunchedEffect
        if (place.address.isNotBlank()) return@LaunchedEffect
        try {
            val address = repository.address(place.latitude, place.longitude)
            if (address.isNotBlank()) selected = place.copy(address = address)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            addressError = true
        }
    }
    LaunchedEffect(mapRetry, mapLoaded) {
        if (!mapLoaded) {
            delay(15_000)
            mapError = true
        }
    }
    fun pick(place: OriginPlace) {
        focus.clearFocus()
        query = ""
        locate = false
        locationMessage = null
        selected = place
        camera = OriginCamera(LatLng(place.latitude, place.longitude), 16f)
    }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding(),
        verticalArrangement = Arrangement.spacedBy(CampusSpacing.Small),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = CampusSpacing.Large),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) { CampusIcon(CampusSymbol.Close, description = "关闭选点") }
            Text("选择起点", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = ::requestLocation, enabled = !locate && !awaitingPermission) {
                Text(if (locate) "定位中…" else "定位")
            }
        }
        CampusTextInput(
            query, { query = it.take(100) },
            label = { Text("搜索地点或地址") },
            singleLine = true,
            leadingIcon = { CampusIcon(CampusSymbol.Search) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                    CampusIcon(CampusSymbol.Close, description = "清除地址搜索")
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = CampusSpacing.Large).testTag("origin_search"),
        )
        if (query.isNotBlank()) {
            when {
                searching -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = CampusSpacing.Large))
                searchError != null -> Row(Modifier.padding(horizontal = CampusSpacing.Large), verticalAlignment = Alignment.CenterVertically) {
                    Text(searchError!!, Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { searchRetry++ }) { Text("重试") }
                }
                suggestions.isEmpty() -> SupportingText("未找到地点，试试城市名或学校名", Modifier.padding(horizontal = CampusSpacing.Large))
                else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = CampusComponents.PlaceResultsHeight)) {
                    items(suggestions, key = { "${it.latitude}:${it.longitude}:${it.title}" }) { place ->
                        Column(
                            Modifier.fillMaxWidth().testTag("origin_result").clickable { pick(place) }.padding(CampusSpacing.Large),
                            verticalArrangement = Arrangement.spacedBy(CampusSpacing.ExtraSmall),
                        ) {
                            Text(place.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (place.address.isNotBlank()) SupportingText(place.address, maxLines = 2)
                        }
                    }
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().testTag("origin_map")) {
            key(mapRetry) {
                OriginMap(selected, camera, ::pick, { mapLoaded = true; mapError = false }, { mapError = true })
            }
            if ((locate || awaitingPermission) && camera == null) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            } else if (mapError) Surface(Modifier.align(Alignment.TopCenter), color = MaterialTheme.colorScheme.errorContainer) {
                Row(Modifier.padding(horizontal = CampusSpacing.Large), verticalAlignment = Alignment.CenterVertically) {
                    Text("地图加载失败，请检查网络或 Key 配置", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { mapLoaded = false; mapError = false; mapRetry++ }) { Text("重试") }
                }
            } else if (!mapLoaded) CircularProgressIndicator(Modifier.align(Alignment.Center).testTag("origin_map_loading"))
        }
        Column(
            Modifier.fillMaxWidth().padding(CampusSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(CampusSpacing.Small),
        ) {
            locationMessage?.let { SupportingText(it, maxLines = 2) }
            Text(selected?.title ?: "点击地图选择起点", maxLines = 1, overflow = TextOverflow.Ellipsis)
            selected?.address?.takeIf { it.isNotBlank() }?.let { SupportingText(it, maxLines = 2) }
            if (addressError) TextButton(onClick = { addressRetry++ }) { Text("地址获取失败，重试") }
            CampusPrimaryButton(
                onClick = { selected?.let(onConfirm) }, enabled = selected != null,
                modifier = Modifier.fillMaxWidth().testTag("confirm_origin"),
            ) { Text("使用此起点") }
        }
    }
}
