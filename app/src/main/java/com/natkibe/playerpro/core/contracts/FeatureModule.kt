package com.natkibe.playerpro.core.contracts

/**
 * Small feature boundary for the Player Pro micro-module style.
 *
 * Each feature should expose a tiny public API through a class ending with Feature.
 * Activities can depend on feature contracts instead of directly depending on every
 * implementation detail. This keeps Codex/agent changes smaller and safer.
 */
interface FeatureModule {
    val name: String
    val milestone: String
    fun isEnabled(): Boolean = true
}
