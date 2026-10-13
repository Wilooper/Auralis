# ReVa GitHub Actions

The repository uses one workflow: `.github/workflows/reva-apk.yml`.

## Build and release behavior

| Event | What the workflow does |
|---|---|
| Push to `main`, pull request to `main`, or manual run | Runs SDK/release-tool tests, JSON validation, Android unit tests and lint; builds ABI APKs; verifies signatures/alignment; uploads APKs and checksums for 14 days. |
| Version tag such as `v0.5.1-dev` or `v1.0.0` | Checks that the tag matches `versionName` and points to a commit reachable from `main`, runs the same checks, builds APKs, then publishes the APKs and checksums as a GitHub Release. |

Development tags containing a hyphen use Android's debug signing key and are marked prerelease. Stable tags require the release signing secrets below and build `assembleRelease`. Do not publish a stable version until the signing key and `versionCode` have been checked.

To make a versioned release:

1. Update `versionName` and increment `versionCode` in `app/build.gradle.kts`.
2. Run and verify the APK build locally.
3. Push the commit to `main` and create a matching tag, for example `v0.5.1-dev` for version `0.5.1-dev`.
4. Download the assets from the resulting GitHub Release and verify `SHA256SUMS.txt`.

Existing releases are never overwritten. Ordinary CI APKs use the Gradle debug key; installing over an APK signed by a different key may require uninstalling the older build, which erases local app data.

## Stable signing secrets

Configure these repository Actions secrets for stable releases. Never add signing keys or passwords to source control:

- `AURALIS_RELEASE_KEYSTORE_BASE64`
- `AURALIS_RELEASE_STORE_PASSWORD`
- `AURALIS_RELEASE_KEY_ALIAS`
- `AURALIS_RELEASE_KEY_PASSWORD`

The workflow writes the decoded key only to runner temporary storage, disables Gradle caches for the signed build, and removes the key after the build.

The build job has read-only repository access. Only the release job receives `contents: write`; it downloads the artifact produced by the matching build job, verifies its checksums, and publishes those exact files. Pull request code never receives release secrets.
