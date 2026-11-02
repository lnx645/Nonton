package id.nonton.app.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

data class FeedPage(
    val items: List<StreamInfoItem>,
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
            FeedPage(page.items.filterIsInstance<StreamInfoItem>(), page.nextPage)
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

    fun clearSessions() {
        synchronized(sessionLock) { sessions.clear() }
    }

    private fun cacheSession(key: String, extractor: ListExtractor<InfoItemAlias>) {
        synchronized(sessionLock) { sessions[key] = extractor }
    }

    private fun session(key: String): ListExtractor<InfoItemAlias>? =
        synchronized(sessionLock) { sessions[key] }

    @Suppress("UNCHECKED_CAST")
    private fun newTrendingExtractor(serviceId: Int): ListExtractor<InfoItemAlias> =
        NewPipe.getService(serviceId).kioskList.defaultKioskExtractor as ListExtractor<InfoItemAlias>

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
