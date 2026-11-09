package ai.venice.vaultview

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.MediaController
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import ai.venice.vaultview.databinding.ActivityViewerBinding
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ViewerActivity : AppCompatActivity() {

    private lateinit var b: ActivityViewerBinding
    private val repo by lazy { VaultRepo(this) }
    private var item: VaultRepo.Item? = null

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityViewerBinding.inflate(layoutInflater)
        setContentView(b.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        b.close.setOnClickListener { finish() }
        b.save.setOnClickListener { save() }
        b.share.setOnClickListener { share() }

        val id = intent.getLongExtra(EXTRA_ID, -1L)
        lifecycleScope.launch {
            val found = withContext(Dispatchers.IO) { repo.byId(id) }
            if (found == null) {
                toast("Item não encontrado")
                finish()
                return@launch
            }
            item = found
            render(found)
        }
    }

    private fun render(it: VaultRepo.Item) {
        val f = File(it.path)
        if (!f.exists()) {
            toast("Arquivo ausente")
            finish()
            return
        }
        b.title.text = it.name
        when (it.kind) {
            "video", "audio" -> {
                b.video.visibility = View.VISIBLE
                b.video.setMediaController(MediaController(this).apply { setAnchorView(b.video) })
                b.video.setVideoURI(Uri.fromFile(f))
                b.video.setOnPreparedListener { mp ->
                    mp.isLooping = true
                    b.video.start()
                }
            }
            else -> {
                b.image.visibility = View.VISIBLE
                Glide.with(this).load(f).fitCenter().into(b.image)
            }
        }
    }

    private fun save() {
        val it = item ?: return
        lifecycleScope.launch {
            val uri = withContext(Dispatchers.IO) { repo.saveToPublic(it) }
            toast(if (uri == null) "Falhou ao salvar" else "Salvo em ${repo.exportDirHint(it)}")
        }
    }

    private fun share() {
        val it = item ?: return
        val i = Intent(Intent.ACTION_SEND).apply {
            type = it.mime ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, repo.uriOf(it))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(i, "Compartilhar"))
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    companion object {
        const val EXTRA_ID = "id"
    }
}
