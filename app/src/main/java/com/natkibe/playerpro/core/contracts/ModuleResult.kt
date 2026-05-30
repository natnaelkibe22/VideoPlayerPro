package com.natkibe.playerpro.core.contracts

sealed class ModuleResult<out T> {
    data class Success<T>(val value: T) : ModuleResult<T>()
    data class Failure(val reason: String, val throwable: Throwable? = null) : ModuleResult<Nothing>()

    inline fun onFailure(block: (Failure) -> Unit): ModuleResult<T> {
        if (this is Failure) block(this)
        return this
    }
}
