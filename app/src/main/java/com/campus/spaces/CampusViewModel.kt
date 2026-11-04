package com.campus.spaces

import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import com.campus.auth.ApiFailure
import com.campus.auth.CampusAccount
import com.campus.auth.HttpAuthRepository
import kotlinx.coroutines.*

enum class CampusTab { Discover, Reviews, Management, Profile }

data class PagedUi<T>(
    val items: List<T> = emptyList(), val total: Int = 0, val page: Int = 0,
    val loading: Boolean = false, val error: String? = null,
    val receivedAt: Long = 0, val warnings: List<String> = emptyList(),
)

data class DetailUi(
    val card: SpaceCard? = null, val loading: Boolean = false,
    val error: String? = null, val receivedAt: Long = 0,
)

data class EditorUi(
    val spaceId: Long, val spaceName: String, val review: MyReview? = null,
    val spaceEnabled: Boolean = true, val environment: Int = 0, val facility: Int = 0,
    val content: String = "", val loading: Boolean = true, val busy: Boolean = false,
    val error: String? = null, val notice: String? = null, val conflict: Boolean = false,
    val confirmWithdraw: Boolean = false, val confirmDiscard: Boolean = false,
    val ready: Boolean = false,
) {
    val dirty: Boolean get() = environment != (review?.environment ?: 0) ||
        facility != (review?.facility ?: 0) || content != (review?.content ?: "")
}

class CampusViewModel : ViewModel() {
    var tab by mutableStateOf(CampusTab.Discover)
        private set
    var filters by mutableStateOf(SpaceFilters())
        private set
    var spaces by mutableStateOf(PagedUi<SpaceCard>())
        private set
    var detail by mutableStateOf(DetailUi())
        private set
    var selectedId by mutableStateOf<Long?>(null)
        private set
    var published by mutableStateOf(PagedUi<PublicReview>())
        private set
    var reviews by mutableStateOf(PagedUi<MyReview>())
        private set
    var reviewFilter by mutableStateOf("")
        private set
    var editor by mutableStateOf<EditorUi?>(null)
        private set
    var sessionExpired by mutableStateOf(false)
        private set
    var nowElapsed by mutableLongStateOf(SystemClock.elapsedRealtime())
        private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var repository: CampusRepository? = null
    private var session: String? = null
    private var generation = 0
    private var spaceJob: Job? = null
    private var detailJob: Job? = null
    private var reviewJob: Job? = null
    private var publicJob: Job? = null
    private var editorJob: Job? = null
    private var searchJob: Job? = null

    fun attach(account: CampusAccount, client: HttpAuthRepository) {
        val key = account.username + "|" + client.hashCode()
        if (session == key) return
        clear()
        session = key
        repository = HttpCampusRepository(client)
        refreshSpaces()
    }

    fun clear() {
        generation++
        listOf(spaceJob, detailJob, reviewJob, publicJob, editorJob, searchJob).forEach { it?.cancel() }
        repository = null
        session = null
        spaces = PagedUi()
        detail = DetailUi()
        published = PagedUi()
        reviews = PagedUi()
        filters = SpaceFilters()
        reviewFilter = ""
        selectedId = null
        editor = null
        tab = CampusTab.Discover
        sessionExpired = false
    }

    fun selectTab(value: CampusTab) {
        if (editor?.busy == true) return
        selectedId = null
        detailJob?.cancel()
        publicJob?.cancel()
        detail = DetailUi()
        published = PagedUi()
        tab = value
        if (value == CampusTab.Reviews && reviews.page == 0) refreshReviews()
        if (value == CampusTab.Discover) refreshSpaces(keepPages = true)
    }

    fun tick() { nowElapsed = SystemClock.elapsedRealtime() }
    fun elapsed(receivedAt: Long) = ((nowElapsed - receivedAt).coerceAtLeast(0) / 1000)

    fun refreshVisible() {
        if (repository == null) return
        if (selectedId != null) refreshDetail()
        else when (tab) {
            CampusTab.Discover -> refreshSpaces(keepPages = true)
            CampusTab.Reviews -> if (editor == null) refreshReviews(keepPages = true)
            CampusTab.Profile, CampusTab.Management -> Unit
        }
    }

    fun search(value: String) {
        filters = filters.copy(name = value.take(120))
        searchJob?.cancel()
        spaceJob?.cancel()
        spaces = PagedUi(loading = filters.hasLocation)
        // 搜索防抖并取消旧请求；请求完成前复核筛选快照，避免旧结果覆盖新条件。
        searchJob = scope.launch {
            delay(400)
            searchJob = null
            refreshSpaces()
        }
    }

    fun applyFilters(value: SpaceFilters) {
        searchJob?.cancel()
        spaceJob?.cancel()
        filters = value
        spaces = PagedUi()
        refreshSpaces()
    }

    fun refreshSpaces(keepPages: Boolean = false, append: Boolean = false) {
        if (!filters.hasLocation) return
        val repo = repository ?: return
        if (spaceJob?.isActive == true || searchJob?.isActive == true) return
        if (append && (spaces.items.size >= spaces.total || spaces.items.size >= MAX_SPACE_RESULTS)) return
        val snapshot = filters
        val epoch = generation
        val old = spaces
        val page = if (append) old.page + 1 else 1
        val size = if (keepPages) (old.page.coerceAtLeast(1) * PAGE_SIZE).coerceAtMost(MAX_SPACE_RESULTS) else PAGE_SIZE
        spaces = spaces.copy(loading = true, error = null)
        spaceJob = scope.launch {
            try {
                val result = repo.spaces(snapshot, page, size)
                if (epoch != generation || snapshot != filters) return@launch
                spaces = PagedUi(
                    items = if (append) (old.items + result.items.map { it.copy(receivedAtElapsed = SystemClock.elapsedRealtime()) }).distinctBy { it.space.id }
                        else result.items.map { it.copy(receivedAtElapsed = SystemClock.elapsedRealtime()) },
                    total = result.total, page = if (keepPages) old.page.coerceAtLeast(1) else page,
                    receivedAt = SystemClock.elapsedRealtime(), warnings = result.warnings,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (epoch == generation && snapshot == filters)
                    spaces = spaces.copy(loading = false, error = messageFor(failure))
            }
        }
    }

    fun loadMoreSpaces() = refreshSpaces(append = true)

    fun openDetail(id: Long) {
        selectedId = id
        detailJob?.cancel()
        publicJob?.cancel()
        detail = DetailUi()
        published = PagedUi()
        refreshDetail()
        refreshPublic()
    }

    fun backToList() {
        if (editor != null) { closeEditor(); return }
        selectedId = null
        detailJob?.cancel()
        publicJob?.cancel()
        detail = DetailUi()
        published = PagedUi()
    }

    fun refreshDetail() {
        val id = selectedId ?: return
        val repo = repository ?: return
        if (detailJob?.isActive == true) return
        val epoch = generation
        detail = detail.copy(loading = true, error = null)
        detailJob = scope.launch {
            try {
                val card = repo.detail(id, filters)
                if (epoch == generation && selectedId == id)
                    detail = DetailUi(card, receivedAt = SystemClock.elapsedRealtime())
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (epoch == generation && selectedId == id)
                    detail = detail.copy(loading = false, error = messageFor(failure))
            }
        }
    }

    fun refreshPublic(append: Boolean = false) {
        val id = selectedId ?: return
        val repo = repository ?: return
        if (publicJob?.isActive == true) return
        if (append && published.items.size >= published.total) return
        val epoch = generation
        val old = published
        val page = if (append) old.page + 1 else 1
        published = published.copy(loading = true, error = null)
        publicJob = scope.launch {
            try {
                val result = repo.publicReviews(id, page)
                if (epoch == generation && selectedId == id)
                    published = PagedUi(
                        if (append) (old.items + result.items).distinctBy { it.id } else result.items,
                        result.total, page,
                    )
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (epoch == generation && selectedId == id)
                    published = published.copy(loading = false, error = messageFor(failure))
            }
        }
    }

    fun changeReviewFilter(value: String) {
        reviewJob?.cancel()
        reviewFilter = value
        reviews = PagedUi()
        refreshReviews()
    }

    fun refreshReviews(keepPages: Boolean = false, append: Boolean = false) {
        val repo = repository ?: return
        if (reviewJob?.isActive == true) return
        if (append && reviews.items.size >= reviews.total) return
        val epoch = generation
        val filter = reviewFilter
        val old = reviews
        reviews = reviews.copy(loading = true, error = null)
        reviewJob = scope.launch {
            try {
                val targetPage = if (append) old.page + 1 else if (keepPages) old.page.coerceAtLeast(1) else 1
                val result = if (keepPages) {
                    val pages = (1..targetPage).map { repo.myReviews(filter, it) }
                    CampusPage(pages.flatMap { it.items }.distinctBy { it.id }, pages.first().total, targetPage, PAGE_SIZE)
                } else repo.myReviews(filter, targetPage)
                if (epoch == generation && filter == reviewFilter)
                    reviews = PagedUi(
                        if (append) (old.items + result.items).distinctBy { it.id } else result.items,
                        result.total, targetPage,
                    )
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (epoch == generation && filter == reviewFilter)
                    reviews = reviews.copy(loading = false, error = messageFor(failure))
            }
        }
    }

    fun openEditor(id: Long, name: String) {
        if (editor?.busy == true) return
        editorJob?.cancel()
        editor = EditorUi(id, name)
        reloadEditor()
    }

    fun reloadEditor() {
        val current = editor ?: return
        val repo = repository ?: return
        if (editorJob?.isActive == true) return
        val epoch = generation
        editor = current.copy(loading = true, error = null)
        editorJob = scope.launch {
            try {
                val value = repo.ownReview(current.spaceId)
                if (epoch == generation && editor?.spaceId == current.spaceId) {
                    val review = value.review
                    if (!ReviewRules.isOlder(editor?.review, review))
                        editor = EditorUi(current.spaceId, current.spaceName, review, value.spaceEnabled,
                            review?.environment ?: 0, review?.facility ?: 0, review?.content.orEmpty(), loading = false, ready = true)
                    else editor = editor?.copy(loading = false)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (epoch == generation && editor?.spaceId == current.spaceId)
                    editor = editor?.copy(loading = false, error = messageFor(failure))
            }
        }
    }

    fun edit(environment: Int? = null, facility: Int? = null, content: String? = null) {
        val value = editor ?: return
        if (value.busy || value.loading || !value.ready || !value.spaceEnabled || value.conflict) return
        editor = value.copy(environment = environment ?: value.environment,
            facility = facility ?: value.facility, content = content?.take(500) ?: value.content,
            error = null, notice = null)
    }

    fun saveReview(withdraw: Boolean = false) {
        val value = editor ?: return
        val repo = repository ?: return
        if (value.busy || value.loading || !value.ready || value.conflict) return
        if (!withdraw && !value.spaceEnabled) return
        if (!withdraw) ReviewRules.validation(value.environment, value.facility, value.content)?.let {
            editor = value.copy(error = it); return
        }
        val epoch = generation
        editor = value.copy(busy = true, error = null, notice = null, confirmWithdraw = false)
        editorJob = scope.launch {
            try {
                val saved = if (withdraw) repo.withdrawReview(value.spaceId, value.review?.version ?: 0)
                    else repo.saveReview(value.spaceId, value.environment, value.facility, value.content, value.review?.version ?: 0)
                if (epoch == generation && editor?.spaceId == value.spaceId) {
                    editor = value.copy(review = saved, environment = saved.environment, facility = saved.facility,
                        content = saved.content, busy = false, confirmWithdraw = false,
                        notice = if (withdraw) "评价已撤回，不再公开或参与均分" else "评价已提交，等待审核")
                    reviewJob?.cancel()
                    refreshReviews(keepPages = true)
                    refreshDetail()
                    if (publicJob?.isActive != true) refreshPublic()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (epoch == generation && editor?.spaceId == value.spaceId)
                    editor = editor?.copy(busy = false, error = messageFor(failure),
                        conflict = failure is ApiFailure && failure.status == 409)
            }
        }
    }

    fun askWithdraw() { editor = editor?.copy(confirmWithdraw = true) }
    fun cancelWithdraw() { editor = editor?.copy(confirmWithdraw = false) }
    fun closeEditor(discard: Boolean = false) {
        val value = editor ?: return
        if (value.busy) return
        if (value.dirty && !discard) editor = value.copy(confirmDiscard = true)
        else { editorJob?.cancel(); editor = null }
    }
    fun cancelDiscard() { editor = editor?.copy(confirmDiscard = false) }

    private fun messageFor(failure: Exception): String {
        if (failure is ApiFailure && failure.status == 401) sessionExpired = true
        return failure.message ?: "加载失败，请重试"
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }
}
