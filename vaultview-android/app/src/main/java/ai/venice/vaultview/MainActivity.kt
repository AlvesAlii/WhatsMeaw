package ai.venice.vaultview

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import ai.venice.vaultview.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var repo: VaultRepo
    private lateinit var adapter: GridAdapter
    private var running = false
    private var pendingSave: VaultRepo.Item? = null
    private var pendingAll = false

    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val writePerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) { toast("Sem permissão de escrita"); return@registerForActivityResult }
        val one = pendingSave
        pendingSave = null
        if (pendingAll) { pendingAll = false; saveAll() } else if (one != null) save(one)
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        repo = VaultRepo(this)

        adapter = GridAdapter(emptyList(), ::open, ::menu)
        b.grid.layoutManager = GridLayoutManager(this, 3)
        b.grid.adapter = adapter

        b.grant.setOnClickListener { askFiles() }
        b.toggle.setOnClickListener { if (running) stop() else start() }
        b.saveAll.setOnClickListener { saveAll() }
        b.refresh.setOnClickListener { refresh() }
        b.filter.setOnCheckedChangeListener { _, _ -> refresh() }

        if (Build.VERSION.SDK_INT >= 33) notifPerm.launch("android.permission.POST_NOTIFICATIONS")
    }

    override fun onResume() {
        super.onResume()
        b.grant.visibility = if (hasFiles()) View.GONE else View.VISIBLE
        refresh()
    }

    private fun hasFiles(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager() else true

    private fun needWrite(): Boolean =
        Build.VERSION.SDK_INT < 29 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED

    private fun askFiles() {
        val i = if (Build.VERSION.SDK_INT >= 30) {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        }
        runCatching { startActivity(i) }
    }

    private fun start() {
        if (!hasFiles()) { askFiles(); return }
        ContextCompat.startForegroundService(this, Intent(this, WatcherService::class.java))
        running = true
        b.toggle.text = "Desligar"
        b.status.text = "Vigiando. Deixe o app aberto em segundo plano."
    }

    private fun stop() {
        stopService(Intent(this, WatcherService::class.java))
        running = false
        b.toggle.text = "Ligar"
        b.status.text = "Parado."
    }

    private fun refresh() = lifecycleScope.launch {
        val list = withContext(Dispatchers.IO) { repo.all(b.filter.isChecked) }
        adapter.submit(list)
        b.status.text = if (list.isEmpty()) {
            "Nada capturado ainda. Abra o WhatsApp e receba uma mídia de visualização única."
        } else {
            "${list.size} itens. Toque para abrir, segure para mais opções."
        }
    }

    private fun save(item: VaultRepo.Item) = lifecycleScope.launch {
        val uri = withContext(Dispatchers.IO) { repo.saveToPublic(item) }
        if (uri == null) toast("Falhou ao salvar") else toast("Salvo em ${repo.exportDirHint(item)}")
    }

    private fun saveAll() {
        val list = adapter.currentList()
        if (list.isEmpty()) { toast("Nada para baixar"); return }
        if (needWrite()) { pendingAll = true; writePerm.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE); return }
        lifecycleScope.launch {
            b.saveAll.isEnabled = false
            b.status.text = "Baixando ${list.size} itens..."
            val results = withContext(Dispatchers.IO) { list.map { repo.saveToPublic(it) != null } }
            b.saveAll.isEnabled = true
            toast("${results.count { it }} de ${list.size} salvos.")
            refresh()
        }
    }

    private fun open(item: VaultRepo.Item) {
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(repo.uriOf(item), item.mime ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(i) }
    }

    private fun menu(item: VaultRepo.Item) {
        MaterialAlertDialogBuilder(this)
            .setTitle(item.name)
            .setItems(arrayOf("Baixar para o aparelho", "Compartilhar", "Marcar como normal", "Apagar")) { _, w ->
                when (w) {
                    0 -> {
                        if (needWrite()) {
                            pendingSave = item
                            writePerm.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        } else save(item)
                    }
                    1 -> {
                        val i = Intent(Intent.ACTION_SEND).apply {
                            type = item.mime ?: "*/*"
                            putExtra(Intent.EXTRA_STREAM, repo.uriOf(item))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(i, "Compartilhar"))
                    }
                    2 -> { repo.markSuspicious(item.id, false); refresh() }
                    3 -> { repo.delete(item); refresh() }
                }
            }.show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}