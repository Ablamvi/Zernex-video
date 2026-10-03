# ZERNEX Video 2.3.1 — test fix

- Replaced the JVM unit-test Uri.EMPTY fixture with explicit test URIs.
- Canonical series keys now use Locale.ROOT for deterministic lower-casing.
- Version bumped to 2.3.1 / versionCode 35.
- CI now runs the explicit `testDebugUnitTest` task with stacktrace so any remaining failing test is visible in the log.
- Release remains minified and resource-shrunk and uses `assembleRelease`.
