package app.auralis

import android.app.Application
import app.auralis.library.LibraryRepository

class ReVaApplication : Application() {
    val extensions by lazy { app.auralis.extensions.ExtensionRegistry(this) }
    val extensionBridge by lazy { app.auralis.extensions.ExtensionBridge(extensions) }
    val library: LibraryRepository by lazy { LibraryRepository(this) }
}
