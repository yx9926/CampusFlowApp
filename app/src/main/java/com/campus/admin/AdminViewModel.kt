package com.campus.admin

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import com.campus.auth.*
import kotlinx.coroutines.*

class AdminViewModel : ViewModel() {
    var page by mutableStateOf<AdminPage?>(null)
        private set
    var list by mutableStateOf(AdminListState())
        private set
    var filters by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var editor by mutableStateOf<AdminEditor?>(null)
        private set
    var notice by mutableStateOf<String?>(null)
        private set
    var sessionExpired by mutableStateOf(false)
        private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var repository: AdminRepository? = null
    private var session: String? = null
    private var account: CampusAccount? = null
    private var generation = 0
    private var loadJob: Job? = null
    private var writeJob: Job? = null

    fun attach(identity: CampusAccount, client: HttpAuthRepository) {
        val key = identity.username + "|" + identity.role + "|" + client.hashCode()
        if (session == key) return
        clear()
        account = identity
        session = key
        repository = HttpAdminRepository(client)
    }

    fun clear() {
        generation++
        loadJob?.cancel()
        writeJob?.cancel()
        repository = null
        session = null
        account = null
        page = null
        list = AdminListState()
        filters = emptyMap()
        editor = null
        notice = null
        sessionExpired = false
    }

    fun open(value: AdminPage) {
        if (value.role != account?.role || editor != null) return
        generation++
        loadJob?.cancel()
        page = value
        filters = when (value) {
            AdminPage.Moderation -> mapOf("status" to "PENDING")
            AdminPage.Inspection -> mapOf("kind" to "PEOPLE")
            else -> emptyMap()
        }
        list = AdminListState()
        notice = null
        refresh()
    }

    fun back() {
        if (editor != null) { closeEditor(); return }
        generation++
        loadJob?.cancel()
        page = null
        list = AdminListState()
        notice = null
    }

    fun applyFilters(values: Map<String, String>) {
        if (editor != null) return
        AdminRules.filterError(values)?.let {
            list = list.copy(error = it)
            return
        }
        generation++
        loadJob?.cancel()
        filters = if (values["kind"] == "VISIT") values - "valid" else values
        list = AdminListState()
        refresh()
    }

    fun nextPage(delta: Int) {
        if (list.loading || editor != null) return
        val target = list.page + delta
        if (target < 1 || target > ((list.result.total + 19) / 20).coerceAtLeast(1)) return
        generation++
        loadJob?.cancel()
        list = AdminListState(page = target)
        refresh()
    }

    fun refresh() {
        val repo = repository ?: return
        val screen = page ?: return
        if (loadJob?.isActive == true || editor?.busy == true) return
        val epoch = generation
        val number = list.page
        val snapshot = filters
        list = list.copy(loading = true, error = null)
        loadJob = scope.launch {
            try {
                val result = repo.load(screen, snapshot, number)
                if (epoch != generation) return@launch
                list = AdminListState(result, number, loaded = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (epoch == generation) list = list.copy(loading = false, error = message(failure))
            }
        }
    }

    fun openEditor(action: AdminAction, row: AdminRow? = null) {
        val current = page ?: return
        if (current.role != account?.role || editor != null) return
        val allowed = when (current) {
            AdminPage.Spaces -> listOf(AdminAction.Space, AdminAction.CreateSpace)
            AdminPage.Hardware -> listOf(AdminAction.Hardware, AdminAction.CreateHardware)
            AdminPage.Moderation -> listOf(AdminAction.Decision)
            AdminPage.Inspection -> listOf(AdminAction.Validity)
            AdminPage.Accounts -> listOf(AdminAction.Account, AdminAction.CreateAccount)
            AdminPage.Config -> listOf(AdminAction.Config)
            AdminPage.Overview -> emptyList()
            AdminPage.Logs -> emptyList()
        }
        if (action !in allowed) return
        val values = when (action) {
            AdminAction.Space -> row?.values.orEmpty() + mapOf("coordinateConfirmed" to if (row?.value("coordinateSystem") == "GCJ02") "true" else "false")
            AdminAction.CreateSpace -> AssetRules.spaceDefaults
            AdminAction.Hardware -> row?.values.orEmpty()
            AdminAction.CreateHardware -> AssetRules.hardwareDefaults
            AdminAction.Decision -> mapOf("action" to if (row?.status == "APPROVED") "REVOKE" else "APPROVE", "reason" to "")
            AdminAction.Validity -> mapOf("reason" to "")
            AdminAction.Account -> row?.values.orEmpty()
            AdminAction.CreateAccount -> mapOf("username" to "", "password" to "", "role" to "USER", "enabled" to "true")
            AdminAction.Config -> row?.values.orEmpty()
        }
        editor = AdminEditor(action, row, values, values)
    }

    fun updateField(key: String, value: String) {
        val state = editor ?: return
        if (state.busy || state.conflict) return
        if (AdminRules.fields(state, list.result).find { it.key == key }?.readOnly == true) return
        var values = state.values + (key to value)
        if (state.action in listOf(AdminAction.Space, AdminAction.CreateSpace) && key in listOf("latitude", "longitude"))
            values = values + ("coordinateConfirmed" to "false")
        editor = state.copy(values = values, error = null)
    }

    fun closeEditor(force: Boolean = false) {
        val state = editor ?: return
        if (state.busy) return
        if (state.dirty && !force) editor = state.copy(discard = true) else editor = null
    }

    fun keepEditing() { editor = editor?.copy(discard = false) }

    fun reloadEditor() {
        val state = editor ?: return
        val repo = repository ?: return
        if (state.busy) return
        if (state.action !in listOf(AdminAction.Account, AdminAction.Config, AdminAction.Hardware)) {
            closeEditor(force = true)
            loadJob?.cancel()
            refresh()
            return
        }
        val epoch = generation
        editor = state.copy(busy = true, error = null)
        writeJob = scope.launch {
            try {
                val row = if (state.action == AdminAction.Account) repo.account(requireNotNull(state.row).id)
                    else if (state.action == AdminAction.Hardware) repo.hardware(requireNotNull(state.row).id)
                    else repo.load(AdminPage.Config, emptyMap(), 1).rows.single()
                if (epoch != generation) return@launch
                editor = AdminEditor(state.action, row, row.values, row.values)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (epoch == generation) editor = state.copy(error = message(failure))
            }
        }
    }

    fun save() {
        val state = editor ?: return
        val repo = repository ?: return
        if (state.busy || state.conflict) return
        if (state.action == AdminAction.Decision && state.row?.status !in listOf("PENDING", "APPROVED")) return
        if (state.action == AdminAction.Config && list.result.rows.firstOrNull()?.version?.let { it > (state.row?.version ?: 0) } == true) return
        AdminRules.validate(state.action, state.values)?.let {
            editor = state.copy(error = it)
            return
        }
        if (state.action == AdminAction.CreateHardware && state.values["deviceId"] !in list.result.candidates) {
            editor = state.copy(error = "请选择发现列表中的设备，或刷新发现列表")
            return
        }
        val epoch = generation
        loadJob?.cancel()
        list = list.copy(loading = false)
        editor = state.copy(busy = true, error = null)
        writeJob = scope.launch {
            try {
                repo.mutate(state)
                if (epoch != generation) return@launch
                editor = null
                notice = "${state.action.title}已完成"
                if (state.action == AdminAction.Account && state.row?.title == account?.username &&
                    (state.values["role"] != account?.role || state.values["enabled"] != "true")) {
                    sessionExpired = true
                    return@launch
                }
                list = list.copy(page = 1)
                refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (epoch == generation) {
                    val conflict = failure is ApiFailure && failure.status == 409 &&
                        (state.action !in listOf(AdminAction.CreateAccount, AdminAction.CreateHardware, AdminAction.Space, AdminAction.CreateSpace)) &&
                        (state.action !in listOf(AdminAction.Account, AdminAction.Hardware) || failure.code == "VERSION_CONFLICT")
                    editor = state.copy(error = message(failure), conflict = conflict)
                }
            }
        }
    }

    private fun message(failure: Exception): String {
        if (failure is ApiFailure && failure.status == 401) sessionExpired = true
        return failure.message ?: "请求失败，请重试"
    }

    override fun onCleared() { scope.cancel() }
}
