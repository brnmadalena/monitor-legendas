package com.brunomadalena.monitorlegendas

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var editTitulo: EditText
    private lateinit var listView: ListView
    private lateinit var txtStatus: TextView
    private lateinit var adapter: ArrayAdapter<String>
    private val executor = Executors.newSingleThreadExecutor()

    companion object {
        const val PERIODIC_WORK_NAME = "verificacao_periodica_legendas"
        const val ONE_TIME_WORK_NAME = "verificacao_manual_legendas"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        editTitulo = findViewById(R.id.edit_titulo)
        listView = findViewById(R.id.list_watchlist)
        txtStatus = findViewById(R.id.txt_status)
        val btnAdicionar: Button = findViewById(R.id.btn_adicionar)
        val btnVerificar: Button = findViewById(R.id.btn_verificar_agora)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        listView.adapter = adapter
        atualizarLista()

        pedirPermissaoNotificacao()
        agendarVerificacaoPeriodica()

        btnAdicionar.setOnClickListener {
            val texto = editTitulo.text.toString().trim()
            if (texto.isEmpty()) {
                Toast.makeText(this, "Digite um título", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val adicionou = WatchlistStore.addTitle(this, texto)
            if (adicionou) {
                editTitulo.text.clear()
                atualizarLista()
                Toast.makeText(this, "Adicionado: $texto", Toast.LENGTH_SHORT).show()
                buscarRetroativamente(texto)
            } else {
                Toast.makeText(this, "\"$texto\" já está na lista", Toast.LENGTH_SHORT).show()
            }
        }

        listView.setOnItemLongClickListener { _, _, position, _ ->
            val titulo = adapter.getItem(position) ?: return@setOnItemLongClickListener true
            AlertDialog.Builder(this)
                .setTitle("Remover título")
                .setMessage("Remover \"$titulo\" da sua lista?")
                .setPositiveButton("Remover") { _, _ ->
                    WatchlistStore.removeTitle(this, titulo)
                    atualizarLista()
                }
                .setNegativeButton("Cancelar", null)
                .show()
            true
        }

        btnVerificar.setOnClickListener {
            txtStatus.text = "Verificando os sites... isso pode levar alguns segundos."
            val request = OneTimeWorkRequestBuilder<SubtitleCheckWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(this)
                .enqueueUniqueWork(ONE_TIME_WORK_NAME, ExistingWorkPolicy.REPLACE, request)

            WorkManager.getInstance(this).getWorkInfoByIdLiveData(request.id)
                .observe(this) { info ->
                    if (info != null && info.state.isFinished) {
                        txtStatus.text = "Verificação concluída. Se algo novo apareceu, você recebeu uma notificação."
                    }
                }
        }
    }

    private fun buscarRetroativamente(tituloNovo: String) {
        executor.execute {
            val tituloNorm = SubtitleCheckWorker.normalize(tituloNovo)
            val jaCatalogados = WatchlistStore.getSeenItems(this)
            val encontrados = jaCatalogados.filter {
                SubtitleCheckWorker.normalize(it.titulo).contains(tituloNorm)
            }
            if (encontrados.isNotEmpty()) {
                NotificationHelper.notify(this, encontrados, retroativo = true)
            }
        }
    }

    private fun atualizarLista() {
        adapter.clear()
        adapter.addAll(WatchlistStore.getWatchlist(this))
        adapter.notifyDataSetChanged()
    }

    private fun pedirPermissaoNotificacao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100
                )
            }
        }
    }

    private fun agendarVerificacaoPeriodica() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<SubtitleCheckWorker>(30, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
