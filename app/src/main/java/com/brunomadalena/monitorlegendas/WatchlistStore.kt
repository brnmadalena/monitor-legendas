package com.brunomadalena.monitorlegendas

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object WatchlistStore {

    private const val PREFS_NAME = "monitor_legendas_prefs"
    private const val KEY_WATCHLIST = "watchlist"
    private const val KEY_SEEN_ITEMS_JSON = "seen_items_json"
    private const val KEY_HAS_RUN_BEFORE = "has_run_before"
    private const val MAX_ITENS_GUARDADOS = 3000

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

    fun getSeenItems(context: Context): List<ItemEncontrado> {
        val json = prefs(context).getString(KEY_SEEN_ITEMS_JSON, null) ?: return emptyList()
        val itens = mutableListOf<ItemEncontrado>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                itens.add(
                    ItemEncontrado(
                        id = o.getString("id"),
                        titulo = o.getString("titulo"),
                        site = o.getString("site"),
                        url = o.getString("url")
                    )
                )
            }
        } catch (e: Exception) {
        }
        return itens
    }

    fun getSeenIds(context: Context): Set<String> =
        getSeenItems(context).map { it.id }.toSet()

    fun addSeenItems(context: Context, novosItens: List<ItemEncontrado>) {
        if (novosItens.isEmpty()) return
        val atuais = getSeenItems(context).toMutableList()
        val idsExistentes = atuais.map { it.id }.toMutableSet()
        for (item in novosItens) {
            if (idsExistentes.add(item.id)) {
                atuais.add(item)
            }
        }
        val limitado = if (atuais.size > MAX_ITENS_GUARDADOS) {
            atuais.takeLast(MAX_ITENS_GUARDADOS)
        } else {
            atuais
        }

        val arr = JSONArray()
        for (item in limitado) {
            val o = JSONObject()
            o.put("id", item.id)
            o.put("titulo", item.titulo)
            o.put("site", item.site)
            o.put("url", item.url)
            arr.put(o)
        }
        prefs(context).edit().putString(KEY_SEEN_ITEMS_JSON, arr.toString()).apply()
    }

    fun hasRunBefore(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HAS_RUN_BEFORE, false)

    fun markHasRunBefore(context: Context) {
        prefs(context).edit().putBoolean(KEY_HAS_RUN_BEFORE, true).apply()
    }
}
