# Third-Party Record

Dependency versions are reproducibly specified, not automatically the newest releases. These choices were compiled during M0; updates require regression tests.

| Component | Pin | License / provenance |
|---|---|---|
| LibVLC Android | `org.videolan.android:libvlc-all:3.7.7` | Published POM: LGPL-2.1; https://code.videolan.org/videolan/libvlcjni |
| Media3 Common / Session / Extractor | `1.6.1` | Apache-2.0; https://github.com/androidx/media; Extractor supplies the ID3 metadata decoder only |
| Compose BOM | `2025.04.01` | AndroidX Apache-2.0; https://developer.android.com/jetpack/compose/bom/bom-mapping |
| Activity Compose | `1.10.1` | AndroidX Apache-2.0 |
| Core KTX | `1.16.0` | AndroidX Apache-2.0 |
| Lifecycle | `2.9.0` | AndroidX Apache-2.0 |
| Coil Compose | `2.7.0` | Apache-2.0; https://github.com/coil-kt/coil |
| Kotlin / Compose plugin | `2.2.10` | Apache-2.0; https://github.com/JetBrains/kotlin |
| Kotlin coroutines | `1.10.2` | Apache-2.0; https://github.com/Kotlin/kotlinx.coroutines |
| JUnit | `4.13.2` | EPL-1.0; https://github.com/junit-team/junit4 |
| Robolectric (test only) | `4.16.1` | Apache-2.0; https://github.com/robolectric/robolectric |
| Android Gradle Plugin | `8.10.1` | Apache-2.0 Android build tools |
| Gradle | `8.11.1` | Apache-2.0; https://gradle.org/ |

LibVLC 3.7.7's AAR requires compile SDK 36. Its published dependencies include Kotlin standard library 2.2.10; the source/compiler pins match that requirement. Original Auralis code is Apache-2.0, not a fork of a GPL player.

Inspected LibVLC AAR SHA-256: `b48dab96e0e90e34cce3861963500c144a5aadb1b249d501d6a4b104d849e61b`.

## Sources Inspected

- https://repo.maven.apache.org/maven2/org/videolan/android/libvlc-all/maven-metadata.xml
- https://repo.maven.apache.org/maven2/org/videolan/android/libvlc-all/3.7.7/libvlc-all-3.7.7.pom
- Published LibVLC 3.7.7 source JAR: `Media`, `MediaPlayer`, `VLCObject`.
- Published Media3 Common 1.6.1 source JAR: `SimpleBasePlayer` state and handlers.
- https://developer.android.com/build/releases/agp-8-10-0-release-notes
- https://developer.android.com/media/media3/session/background-playback

## Public-Release Blockers

The source pack includes the original application source and license texts, not the complete corresponding native source/build recipe for the packaged LibVLC binaries. Before publishing binaries, identify exact LibVLC/VLC commits, transitive native codecs, all notices and the required source/relinking distribution mechanism. The Java bindings source JAR alone does not cover every native library.

Create a complete SBOM/license inventory for resolved transitive dependencies. Add accessible in-app notices and source information. Preserve upstream copyrights. Review obligations for each platform and distribution channel. These tasks are unfinished; this is a development prototype, not a compliance-approved public release.

No music, third-party brand artwork, proprietary UI assets, online extractor implementation or ML weights are bundled. The `app.auralis` application ID is provisional and has not been checked for ownership or store conflicts. Auralis's name has not undergone trademark clearance.
