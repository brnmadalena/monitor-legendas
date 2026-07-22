package com.brunomadalena.monitorlegendas

import android.content.Context
import android.content.SharedPreferences

/**
 * Guarda a lista de títulos que o usuário quer acompanhar,
 * e o histórico de itens já vistos (pra não avisar duas vezes
 * da mesma coisa).
 */
object WatchlistStore {

    private const val PREFS_NAME = "monitor_legendas_prefs"
    private const val KEY_WATCHLIST = "watchlist"
    private const val KEY_SEEN_IDS = "seen_ids"
    private const val KEY_HAS_RUN_BEFORE = "has_run_before"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getWatchlist(context: Context): List<String> {
        val raw = prefs(context).getStringSet(KEY_WATCHLIST, emptySet()) ?: emptySet()
        return raw.toList().sorted()
    }

    fun addTitle(context: Context, titulo: String): Boolean {
        val atual = prefs(context).getStringSet(KEY_WATCHLIST, emptySet())?.toMutableSet()
            ?: mutableSetOf()
        val jaExiste = atual.any { it.equals(titulo, ignoreCase = true) }
        if (jaExiste) return false
        atual.add(titulo.trim())
        prefs(context).edit().putStringSet(KEY_WATCHLIST, atual).apply()
        return true
    }

    fun removeTitle(context: Context, titulo: String) {
        val atual = prefs(context).getStringSet(KEY_WATCHLIST, emptySet())?.toMutableSet()
            ?: mutableSetOf()
        atual.removeAll { it.equals(titulo, ignoreCase = true) }
        prefs(context).edit().putStringSet(KEY_WATCHLIST, atual).apply()
    }

    fun getSeenIds(context: Context): MutableSet<String> {
        return prefs(context).getStringSet(KEY_SEEN_IDS, emptySet())?.toMutableSet()
            ?: mutableSetOf()
    }

    fun saveSeenIds(context: Context, ids: Set<String>) {
        // SharedPreferences com StringSet tem limite prático; mantemos só os
        // últimos 3000 ids pra não crescer pra sempre.
        val limitado = if (ids.size > 3000) ids.toList().takeLast(3000).toSet() else ids
        prefs(context).edit().putStringSet(KEY_SEEN_IDS, limitado).apply()
    }

    fun hasRunBefore(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HAS_RUN_BEFORE, false)

    fun markHasRunBefore(context: Context) {
        prefs(context).edit().putBoolean(KEY_HAS_RUN_BEFORE, true).apply()
    }
}
