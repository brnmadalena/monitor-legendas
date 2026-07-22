package com.brunomadalena.monitorlegendas

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.text.Normalizer

data class ItemEncontrado(val id: String, val titulo: String, val site: String, val url: String)

class SubtitleCheckWorker(appContext: Context, params: WorkerParameters) :
    Worker(appContext, params) {

    companion object {
        private const val ADDIC7ED_URL = "https://www.addic7ed.com/user/1067371"
        private const val SUBDL_URL = "https://subdl.com/u/LosChulosTeam"
        private const val OPENSUBTITLES_URL =
            "https://www.opensubtitles.org/en/search/sublanguageid-all/iduser-8949618/a-mysqld"

        private val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        fun normalize(texto: String): String {
            val semAcento = Normalizer.normalize(texto.lowercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
            return semAcento.replace(Regex("\\s+"), " ").trim()
        }

        fun fetchDocument(url: String): Document? {
            return try {
                Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .header("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8")
                    .timeout(20000)
                    .get()
            } catch (e: Exception) {
                null
            }
        }

        fun fetchAddic7ed(): List<ItemEncontrado> {
            val doc = fetchDocument(ADDIC7ED_URL) ?: return emptyList()
            val itens = mutableListOf<ItemEncontrado>()
            for (a in doc.select("a[href]")) {
                val href = a.attr("href")
                if (Regex("/(serie|movie)/").containsMatchIn(href)) {
                    val titulo = a.text().trim()
                    if (titulo.isEmpty()) continue
                    val fullUrl = if (href.startsWith("http")) href else "https://www.addic7ed.com$href"
                    itens.add(ItemEncontrado(fullUrl, titulo, "Addic7ed", fullUrl))
                }
            }
            return itens
        }

        fun fetchSubdl(): List<ItemEncontrado> {
            val doc = fetchDocument(SUBDL_URL) ?: return emptyList()
            val itens = mutableListOf<ItemEncontrado>()
            for (a in doc.select("a[href]")) {
                val href = a.attr("href")
                if (href.contains("/s/info/")) {
                    val titulo = a.text().trim()
                    if (titulo.isEmpty()) continue
                    val fullUrl = if (href.startsWith("http")) href else "https://subdl.com$href"
                    itens.add(ItemEncontrado(fullUrl, titulo, "Subdl", fullUrl))
                }
            }
            return itens
        }

        fun fetchOpenSubtitles(): List<ItemEncontrado> {
            val doc = fetchDocument(OPENSUBTITLES_URL) ?: return emptyList()
            val itens = mutableListOf<ItemEncontrado>()
            for (a in doc.select("a[href]")) {
                val href = a.attr("href")
                if (href.contains("/subtitles/") || href.contains("/en/subtitleserve/")) {
                    val titulo = a.text().trim()
                    if (titulo.length < 2) continue
                    val fullUrl = if (href.startsWith("http")) href else "https://www.opensubtitles.org$href"
                    itens.add(ItemEncontrado(fullUrl, titulo, "OpenSubtitles", fullUrl))
                }
            }
            return itens
        }

        fun fetchTodosOsSites(): List<ItemEncontrado> {
            val todos = mutableListOf<ItemEncontrado>()
            todos += fetchAddic7ed()
            todos += fetchSubdl()
            todos += fetchOpenSubtitles()
            return todos
        }
    }

    override fun doWork(): Result {
        return try {
            val context = applicationContext
            NotificationHelper.createChannel(context)

            val watchlist = WatchlistStore.getWatchlist(context)
            val todosItens = fetchTodosOsSites()
            val seenIds = WatchlistStore.getSeenIds(context)
            val primeiraExecucao = !WatchlistStore.hasRunBefore(context)

            val novosItens = todosItens.filter { !seenIds.contains(it.id) }

            if (watchlist.isNotEmpty() && !primeiraExecucao) {
                val watchlistNorm = watchlist.map { normalize(it) }
                val paraAvisar = novosItens.filter { item ->
                    val tituloNorm = normalize(item.titulo)
                    watchlistNorm.any { tituloNorm.contains(it) }
                }
                if (paraAvisar.isNotEmpty()) {
                    NotificationHelper.notify(context, paraAvisar, retroativo = false)
                }
            }

            WatchlistStore.markHasRunBefore(context)
            WatchlistStore.addSeenItems(context, novosItens)

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
