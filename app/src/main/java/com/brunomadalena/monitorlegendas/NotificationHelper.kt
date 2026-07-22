package com.brunomadalena.monitorlegendas

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {
    const val CHANNEL_ID = "monitor_legendas_channel"
    private const val NOTIFICATION_ID_PERIODICO = 1001
    private const val NOTIFICATION_ID_RETROATIVO = 1002

    fun createChannel(context: Context) {
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

    fun notify(context: Context, itens: List<ItemEncontrado>, retroativo: Boolean = false) {
        if (itens.isEmpty()) return
        createChannel(context)

        val titulo = if (retroativo) {
            "Já tem legenda disponível!"
        } else if (itens.size == 1) {
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

        val id = if (retroativo) NOTIFICATION_ID_RETROATIVO else NOTIFICATION_ID_PERIODICO
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }
}
