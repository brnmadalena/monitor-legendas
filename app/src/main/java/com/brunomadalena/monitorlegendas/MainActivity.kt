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
                Toast.makeText(this, "\"$texto\" já
