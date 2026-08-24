package com.hasanDroid.le5ascorer

import com.hasanDroid.le5ascorer.data.repository.LeekhaRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Reaches the running app's production repository so the screenshot walk can
 * seed deterministic fixtures into it.
 *
 * This lives in the debug source set rather than in androidTest, which is where
 * it started. Hilt aggregates entry points when it generates the *app's*
 * component, and androidTest compiles after that — so a @EntryPoint declared
 * there is never implemented by the app's SingletonCImpl, and
 * EntryPointAccessors.fromApplication fails at runtime with:
 *
 *     ClassCastException: Cannot cast ...SingletonC$SingletonCImpl
 *                         to ScreenshotSeedEntryPoint
 *
 * The debug source set is part of the app variant that Hilt processes, so the
 * generated component does implement this, and androidTest can still see it.
 * It stays out of release builds either way.
 *
 * Seeding has to go through the app's own singleton graph — not a second Room
 * instance opened on the same file — because Room's invalidation tracker is
 * per-instance. Writes made outside the app's instance would never notify the
 * Flows the UI is collecting, so the screens would photograph empty.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ScreenshotSeedEntryPoint {
    fun repository(): LeekhaRepository
}
