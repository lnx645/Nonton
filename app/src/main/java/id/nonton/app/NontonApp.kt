package id.nonton.app

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import id.nonton.app.core.DownloaderImpl
import id.nonton.app.core.SettingsStore
import id.nonton.app.data.AppDatabase
import id.nonton.app.data.LibraryRepository
import id.nonton.app.core.ExtractorRepository
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.Locale

class NontonApp : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        val locale = Locale.getDefault()
        val localization = Localization.fromLocale(locale)
        val country = ContentCountry(
            locale.country.ifBlank { "US" }
        )
        NewPipe.init(DownloaderImpl, localization, country)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(DownloaderImpl.client)
            .crossfade(true)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.2)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build()
            }
            .build()
}

class AppContainer(context: Context) {
    val settings = SettingsStore(context)
    val database: AppDatabase = AppDatabase.build(context)
    val library = LibraryRepository(database)
    val extractor = ExtractorRepository()
}

val Context.container: AppContainer
    get() = (applicationContext as NontonApp).container
