package app.auralis

import android.app.Application
import app.auralis.library.LibraryRepository
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import coil.request.CachePolicy

class ReVaApplication : Application(), ImageLoaderFactory {
    val companions by lazy { app.auralis.extensions.CompanionManager(this) }
    val extensions by lazy { app.auralis.extensions.ExtensionRegistry(this) }
    val extensionBridge by lazy { app.auralis.extensions.ExtensionBridge(extensions) }
    val library: LibraryRepository by lazy { LibraryRepository(this) }
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizeBytes((Runtime.getRuntime().maxMemory() / 10).coerceAtMost(24L * 1024 * 1024).toInt()).build() }
        // ArtworkProvider already owns the bounded original cache; avoid duplicating it on disk.
        .diskCachePolicy(CachePolicy.DISABLED)
        .build()
}
