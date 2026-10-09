# GitHub automation

## Fixed SDK setup

The original workflow assumed `sdkmanager` was on PATH. The shared Android setup now installs command-line tools, platform 36 and build tools 35.0.0 explicitly, configures JDK 17 and Gradle, and checks the installed paths. There is no fragile `yes | sdkmanager` pipeline in the workflow.

## What runs automatically

| Workflow / bot | Trigger | Result |
|---|---|---|
| Android Checks | Push to main, PR to main, manual | SDK and release-tool tests, JSON syntax checks, actionlint, Android unit tests, lint, all debug APKs, signature/alignment verification and SHA256SUMS |
| CodeQL | Main pushes, PRs, weekly Monday, manual | Java/Kotlin (manual traced build), Python, TypeScript and Actions security analysis |
| Dependency Review | PRs | Fails the check for newly introduced high/critical known vulnerabilities; summary stays in the run, including fork PRs |
| Gradle Dependency Graph | Main build/dependency changes, manual | Submits resolved runtime dependencies for GitHub dependency graph/alerts |
| Dependabot | Weekly Monday | Grouped Actions and Gradle update PRs; no auto-merge |
| Issue and PR triage | New/reopened issues; PR changes | Creates and applies a bounded set of type/area labels; never runs PR code |
| CI failure reporter | Android Checks completes on main | Maintains one bot-created failure issue; closes it when the current main commit passes |
| Tagged Release | Push a version tag, manual on a tag | Validates source/version, reruns tests/lint, builds APKs, packages source/SDK/checksums, then publishes a GitHub release |

APKs: open the Android Checks run and download `auralis-debug-apks-<run-id>` from Artifacts. Reports and APKs are kept for 14 days. Workflow logs report real failures; none are hidden with `continue-on-error`. CodeQL uploads appear in Security → Code scanning. Dependency Review only checks dependencies GitHub can resolve; it is not a complete security audit.

## Releases

1. Finish physical-device and native-license checks. Update `versionName` **and increment** `versionCode` in `app/build.gradle.kts` and commit on main.
2. Push a version tag matching `versionName`, e.g. `v0.5.0-dev` for `0.5.0-dev`. The tagged commit must be reachable from main.
3. The Tagged Release workflow runs automatically. A hyphenated version is a development prerelease using debug APKs. A stable version such as `v0.5.0` requires production signing secrets and produces release APKs.

Manual reruns must select an existing version tag, not a branch. Existing releases are never overwritten. If publishing fails after creating a draft, inspect that draft; do not delete or replace a published release merely to retry. No version tag is created automatically on every commit, so normal pushes do not spam Releases.

### Signing: one-time maintainer setup

Add these **repository Actions secrets**, never files in Git or values in chat:

- `AURALIS_RELEASE_KEYSTORE_BASE64`: base64-encoded production keystore.
- `AURALIS_RELEASE_STORE_PASSWORD`: store password.
- `AURALIS_RELEASE_KEY_ALIAS`: signing alias.
- `AURALIS_RELEASE_KEY_PASSWORD`: key password.

The key is decoded only in runner temporary storage with restrictive permissions and removed when the signing step exits. Signing jobs disable Gradle caching/configuration cache and use no persistent daemon. Fork PRs and ordinary builds do not receive these secrets. Without the secrets, stable publication fails clearly; it never silently falls back to a debug key.

**CI debug APKs use temporary keys.** They are not signed with the separately saved prototype key, so they may require uninstalling an earlier installation. Uninstalling clears Auralis data/settings. Back up first. Retain and securely back up the production key for stable upgrades; do not publish or use the prototype debug key for production.

## Repository settings requiring an owner

These workflows do not silently change account/repository security settings. Check that:

- Actions is allowed to run the pinned actions in this repo.
- Dependency graph and Dependabot alerts/security updates are enabled. Version-update PRs are configured by `dependabot.yml`; the alert/security-update settings are separate.
- CodeQL uses **advanced setup** (the committed workflow). Do not run conflicting default and advanced setups. Do not weaken existing repository protections.
- A main-branch ruleset requires Android Checks, Dependency Review and CodeQL checks once their exact check names have appeared; disallow unreviewed merges as appropriate. Workflows alone cannot enforce required checks.
- Protect version tags, and optionally require approval of release changes before tagging. Do not allow untrusted contributors to create signing-triggering tags.

The automation does not install a paid external review app or configure Copilot auto-review. Static review comes from CodeQL, Android lint, actionlint, dependency review and regression tests. Maintainer review remains necessary, especially for permissions, signing workflows and native playback.

## Security and resource limits

External actions are commit-pinned; Dependabot can propose pin updates. Checks use read-only tokens and no persisted checkout credentials. Only dependency submission, metadata triage/reporting and release publishing have scoped write permissions. Privileged triage/reporting never checks out contributor code; release publishing downloads only the same workflow's verified build artifact and does not execute it. CI has concurrency cancellation for superseded checks, explicit timeouts and short artifact retention. No auto-merge or mass-closing of user issues is enabled.

## References

- [Android setup action](https://github.com/android-actions/setup-android)
- [CodeQL compiled languages](https://docs.github.com/en/code-security/how-tos/find-and-fix-code-vulnerabilities/manage-your-configuration/codeql-for-compiled-languages)
- [Dependency Review](https://github.com/actions/dependency-review-action)
- [Gradle dependency submission](https://github.com/gradle/actions/tree/main/dependency-submission)
- [actionlint](https://github.com/rhysd/actionlint)
