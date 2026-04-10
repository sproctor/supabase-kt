# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

supabase-kt is a Kotlin Multiplatform client library for Supabase. It targets JVM, Android, iOS, macOS, watchOS, tvOS, Linux, Windows (mingwX64), JS, and WasmJS.

## Build Commands

```bash
# Build all library modules (excludes samples)
./gradlew -DLibrariesOnly=true build

# Build a specific module
./gradlew :auth-kt:build
./gradlew :postgrest-kt:build
./gradlew :storage-kt:build
./gradlew :realtime-kt:build
./gradlew :functions-kt:build
./gradlew :supabase-kt:build

# Run tests for a specific module (JVM)
./gradlew :auth-kt:jvmTest
./gradlew :postgrest-kt:jvmTest

# Run a single test class
./gradlew :auth-kt:jvmTest --tests "AuthTest"

# Run detekt (static analysis) across all modules
./gradlew detektAll

# Run integration tests (requires local Supabase instance via `supabase start` in integration-test/supabase/)
./gradlew :integration-test:test -Pintegration

# Publish to local Maven for testing changes in another project
./gradlew -DLibrariesOnly=true -DDisableSigning=true -DSupabaseVersion="custom" publishToMavenLocal
```

## Module Structure

Directory names map to Gradle module names via settings.gradle.kts renames:
- `Auth/` -> `:auth-kt`
- `Postgrest/` -> `:postgrest-kt`
- `Storage/` -> `:storage-kt`
- `Realtime/` -> `:realtime-kt`
- `Functions/` -> `:functions-kt`
- `Supabase/` -> `:supabase-kt` (core module)

Non-library modules (excluded from publishing): `plugins`, `serializers`, `test-common`, `integration-test`.

## Architecture

**Plugin system**: Each Supabase feature (Auth, Postgrest, Storage, Realtime, Functions) is a plugin that implements `SupabasePlugin<Config>` and is installed into a `SupabaseClient` via `SupabasePluginProvider`. This is inspired by Ktor's plugin architecture. The core module (`Supabase/`) defines the client, plugin interfaces, serialization, and networking abstractions.

**Source set hierarchy**: Modules use Kotlin Multiplatform with a custom hierarchy defined in `buildSrc/`. Key custom groups:
- `androidAndJvm` - shared code for Android and JVM targets
- `settings` - targets that support persistent settings (JVM, Android, JS, mingwX64, Apple, WasmJS)
- `desktop` - mingwX64, macOS, Linux
- Platform-specific `expect`/`actual` declarations are in target-specific source sets

**buildSrc conventions**: Shared Gradle configuration lives in `buildSrc/src/main/kotlin/`:
- `KotlinTargets.kt` - target definitions (`allTargets()`, `jvmTargets()`, etc.)
- `KotlinPlugin.kt` - `defaultConfig()` with default opt-ins
- `TargetHierarchy.kt` - custom source set groups
- `Detekt.kt`, `Dokka.kt`, `PowerAssert.kt`, `Publishing.kt` - plugin configurations

## Code Style

- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- 4 spaces for indentation, no wildcard (`*`) imports
- Public API must have KDoc documentation (enforced by detekt); classes/functions annotated with `@SupabaseInternal` or `@SupabaseExperimental` are exempt
- Tests use kotlin-power-assert for assertions
- PR target branch is `master`
