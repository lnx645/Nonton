package id.nonton.app.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.comments.CommentsExtractor
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

data class FeedPage(
    val items: List<StreamInfoItem>,
    val nextPage: Page?,
)

data class CommentsPage(
    val items: List<CommentsInfoItem>,
    val nextPage: Page?,
    val totalCount: Int,
    val disabled: Boolean,
)

data class ChannelData(
    val info: ChannelInfo,
    val videos: List<StreamInfoItem>,
    val nextPage: Page?,
)

/**
 * All extractor calls run on the IO dispatcher. List extractor instances are cached briefly so
 * that pagination can reuse their internal state (some services need it).
 */
class ExtractorRepository {

    private val sessions = object : LinkedHashMap<String, ListExtractor<InfoItemAlias>>(6, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, ListExtractor<InfoItemAlias>>?,
        ): Boolean = size > 6
    }

    private val sessionLock = Any()

    private val commentSessions =
        object : LinkedHashMap<String, ListExtractor<CommentsInfoItem>>(6, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, ListExtractor<CommentsInfoItem>>?,
            ): Boolean = size > 6
        }

    suspend fun trending(serviceId: Int, nextPage: Page? = null): Result<FeedPage> =
        extract {
            val key = "trending:$serviceId"
            val extractor = if (nextPage == null) {
                newTrendingExtractor(serviceId).also { it.fetchPage() }
                    .also { cacheSession(key, it) }
            } else {
                session(key) ?: newTrendingExtractor(serviceId).also { it.fetchPage() }
            }
            val page = if (nextPage == null) extractor.initialPage else extractor.getPage(nextPage)
            val items = page.items.filterIsInstance<StreamInfoItem>()
            if (nextPage == null) {
                val counts = items.groupingBy { it.streamType?.name ?: "null" }.eachCount()
                android.util.Log.d(
                    "NontonFeed",
                    "trending service=$serviceId total=${items.size} types=$counts",
                )
            }
            FeedPage(items, page.nextPage)
        }

    suspend fun search(
        serviceId: Int,
        query: String,
        nextPage: Page? = null,
    ): Result<FeedPage> = extract {
        val key = "search:$serviceId:$query"
        val extractor = if (nextPage == null) {
            newSearchExtractor(serviceId, query).also { it.fetchPage() }
                .also { cacheSession(key, it) }
        } else {
            session(key) ?: newSearchExtractor(serviceId, query).also { it.fetchPage() }
        }
        val page = if (nextPage == null) extractor.initialPage else extractor.getPage(nextPage)
        FeedPage(page.items.filterIsInstance<StreamInfoItem>(), page.nextPage)
    }

    suspend fun stream(serviceId: Int, url: String): Result<StreamInfo> = extract {
        StreamInfo.getInfo(NewPipe.getService(serviceId), url)
    }

    suspend fun channel(serviceId: Int, url: String): Result<ChannelData> =
        extract {
            val key = "channel:$serviceId:$url"
            val service = NewPipe.getService(serviceId)
            val info = ChannelInfo.getInfo(service, url)
            val tab = info.tabs.firstOrNull { it.url.contains("videos", ignoreCase = true) }
                ?: info.tabs.firstOrNull()
            if (tab == null) {
                ChannelData(info, emptyList(), null)
            } else {
                val extractor = service.getChannelTabExtractor(tab)
                extractor.fetchPage()
                cacheSession(key, extractor)
                val page = extractor.initialPage
                ChannelData(
                    info,
                    page.items.filterIsInstance<StreamInfoItem>(),
                    page.nextPage,
                )
            }
        }

    suspend fun channelMore(serviceId: Int, url: String, nextPage: Page): Result<FeedPage> =
        extract {
            val key = "channel:$serviceId:$url"
            val extractor = session(key)
                ?: throw IllegalStateException("Sesi channel kedaluwarsa, muat ulang.")
            val page = extractor.getPage(nextPage)
            FeedPage(page.items.filterIsInstance<StreamInfoItem>(), page.nextPage)
        }

    suspend fun comments(
        serviceId: Int,
        url: String,
        nextPage: Page? = null,
    ): Result<CommentsPage> = extract {
        val key = "comments:$serviceId:$url"
        val extractor: CommentsExtractor = if (nextPage == null) {
            NewPipe.getService(serviceId).getCommentsExtractor(url)
                .also { it.fetchPage() }
                .also { synchronized(sessionLock) { commentSessions[key] = it } }
        } else {
            (synchronized(sessionLock) { commentSessions[key] } as? CommentsExtractor)
                ?: NewPipe.getService(serviceId).getCommentsExtractor(url)
                    .also { it.fetchPage() }
        }
        val disabled = runCatching { extractor.isCommentsDisabled }.getOrDefault(false)
        val total = runCatching { extractor.commentsCount }.getOrDefault(-1)
        val page = if (nextPage == null) extractor.initialPage else extractor.getPage(nextPage)
        CommentsPage(
            items = page.items.filterIsInstance<CommentsInfoItem>(),
            nextPage = page.nextPage,
            totalCount = total,
            disabled = disabled,
        )
    }

    fun clearSessions() {
        synchronized(sessionLock) {
            sessions.clear()
            commentSessions.clear()
        }
    }

    private fun cacheSession(key: String, extractor: ListExtractor<InfoItemAlias>) {
        synchronized(sessionLock) { sessions[key] = extractor }
    }

    private fun session(key: String): ListExtractor<InfoItemAlias>? =
        synchronized(sessionLock) { sessions[key] }

    @Suppress("UNCHECKED_CAST")
    private fun newTrendingExtractor(serviceId: Int): ListExtractor<InfoItemAlias> =
        NewPipe.getService(serviceId).kioskList.getDefaultKioskExtractor() as ListExtractor<InfoItemAlias>

    @Suppress("UNCHECKED_CAST")
    private fun newSearchExtractor(serviceId: Int, query: String): ListExtractor<InfoItemAlias> =
        NewPipe.getService(serviceId).getSearchExtractor(query) as ListExtractor<InfoItemAlias>

    private suspend fun <T> extract(block: () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/** Alias keeps the generic signature readable above. */
private typealias InfoItemAlias = org.schabi.newpipe.extractor.InfoItem
