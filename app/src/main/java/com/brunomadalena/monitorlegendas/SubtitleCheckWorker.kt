package com.brunomadalena.monitorlegendas

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.text.Normalizer

data class ItemEncontrado(val id: String, val titulo: String, val site: String, val url: String)

class SubtitleCheckWorker(appContext: Context, params: WorkerParameters) :
    Worker(appContext, params) {

    companion object {
        const val CHANNEL_ID = "monitor_legendas_channel"
        const val NOTIFICATION_ID = 1001

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
    }

    override fun doWork(): Result {
        return try {
            val context = applicationContext
            createNotificationChannel(context)

            val watchlist = WatchlistStore.getWatchlist(context)
            if (watchlist.isEmpty()) {
                return Result.success()
            }
            val watchlistNorm = watchlist.map { normalize(it) }

            val todosItens = mutableListOf<ItemEncontrado>()
            todosItens += fetchAddic7ed()
            todosItens += fetchSubdl()
            todosItens += fetchOpenSubtitles()

            val seenIds = WatchlistStore.getSeenIds(context)
            val primeiraExecucao = !WatchlistStore.hasRunBefore(context)

            val novosIds = mutableListOf<String>()
            val paraAvisar = mutableListOf<ItemEncontrado>()

            for (item in todosItens) {
                if (seenIds.contains(item.id)) continue
                novosIds.add(item.id)
                val tituloNorm = normalize(item.titulo)
                if (watchlistNorm.any { tituloNorm.contains(it) }) {
                    paraAvisar.add(item)
                }
            }

            if (!primeiraExecucao && paraAvisar.isNotEmpty()) {
                enviarNotificacao(context, paraAvisar)
            }
            WatchlistStore.markHasRunBefore(context)

            seenIds.addAll(novosIds)
            WatchlistStore.saveSeenIds(context, seenIds)

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun fetchDocument(url: String): Document? {
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

    private fun fetchAddic7ed(): List<ItemEncontrado> {
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

    private fun fetchSubdl(): List<ItemEncontrado> {
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

    private fun fetchOpenSubtitles(): List<ItemEncontrado> {
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

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Avisos de legendas",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Avisa quando sai legenda de um título da sua lista"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun enviarNotificacao(context: Context, itens: List<ItemEncontrado>) {
        val titulo = if (itens.size == 1) {
            "Legenda nova: ${itens[0].titulo}"
        } else {
            "${itens.size} novidades na sua watchlist"
        }
        val corpo = itens.joinToString("\n") { "[${it.site}] ${it.titulo}" }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(titulo)
            .setContentText(corpo.take(100))
            .setStyle(NotificationCompat.BigTextStyle().bigText(corpo))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }
}
