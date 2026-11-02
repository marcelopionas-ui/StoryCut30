package com.storycut.app

import android.app.Activity
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import kotlin.math.min

@UnstableApi
class MainActivity : ComponentActivity() {
    private var source: Uri? = null
    private var lengthMs = 0L
    private var stepMs = 30_000L
    private var index = 0
    private var shareIndex = 0
    private val clips = mutableListOf<File>()
    private lateinit var status: TextView
    private lateinit var seconds: EditText
    private lateinit var cutButton: Button
    private lateinit var shareButton: Button
    private lateinit var account: RadioGroup
    private var transformer: Transformer? = null
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            source = uri
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val r = MediaMetadataRetriever()
            try { r.setDataSource(this, uri); lengthMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                status.text = "Vídeo selecionado: ${lengthMs / 1000}s"
            } catch (e: Exception) { status.text = "Erro ao abrir vídeo: ${e.message}" } finally { r.release() }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(36, 48, 36, 30) }
        fun title(s: String) = TextView(this).apply { text=s; textSize=20f; setPadding(0,14,0,14) }
        root.addView(title("StoryCut — Cortador para Stories"))
        val pick = Button(this).apply { text="Selecionar vídeo"; setOnClickListener { picker.launch(arrayOf("video/*")) } }
        root.addView(pick)
        root.addView(title("Duração de cada take (segundos)"))
        seconds = EditText(this).apply { inputType=2; setText("30"); hint="Ex.: 30" }
        root.addView(seconds)
        cutButton = Button(this).apply { text="Cortar vídeo"; setOnClickListener { startCut() } }
        root.addView(cutButton)
        root.addView(title("Destino da publicação"))
        account = RadioGroup(this).apply { orientation=RadioGroup.VERTICAL }
        val personal = RadioButton(this).apply { id=100; text="Instagram pessoal — compartilhamento assistido"; isChecked=true }
        val business = RadioButton(this).apply { id=101; text="Instagram empresarial — compartilhamento assistido (API futura)" }
        account.addView(personal); account.addView(business); root.addView(account)
        shareButton = Button(this).apply { text="Compartilhar próximo take no Instagram"; isEnabled=false; setOnClickListener { shareNext() } }
        root.addView(shareButton)
        status = title("Escolha um vídeo para começar."); root.addView(status)
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
    }
    private fun startCut() {
        val uri = source ?: run { status.text="Selecione um vídeo primeiro"; return }
        val secs = seconds.text.toString().toLongOrNull()
        if (secs == null || secs < 1 || secs > 3600 || lengthMs <= 0) { status.text="Informe de 1 a 3600 segundos e escolha um vídeo válido"; return }
        stepMs = secs * 1000L; index=0; shareIndex=0; clips.clear(); cutButton.isEnabled=false; shareButton.isEnabled=false
        val dir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "storycut_${System.currentTimeMillis()}")
        if (!dir.mkdirs()) { status.text="Não foi possível criar a pasta"; cutButton.isEnabled=true; return }
        exportNext(uri,dir)
    }
    private fun exportNext(uri: Uri, dir: File) {
        val start = index * stepMs
        if (start >= lengthMs) { status.text="Pronto! ${clips.size} takes. Publique cada um e depois toque em Próximo."; cutButton.isEnabled=true; shareButton.isEnabled=clips.isNotEmpty(); return }
        val end = min(start+stepMs,lengthMs)
        val output = File(dir, "take_${(index+1).toString().padStart(4,'0')}.mp4")
        val mediaItem = MediaItem.Builder().setUri(uri).setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder().setStartPositionMs(start).setEndPositionMs(end).build()
        ).build()
        status.text="Processando take ${index+1} (${start/1000}s–${end/1000}s)..."
        transformer = Transformer.Builder(this).addListener(object : Transformer.Listener {
            override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: ExportResult) {
                clips.add(output); index++; exportNext(uri,dir)
            }
            override fun onError(composition: androidx.media3.transformer.Composition, exportResult: ExportResult, exportException: ExportException) {
                status.text="Erro no take ${index+1}: ${exportException.message}"; cutButton.isEnabled=true
            }
        }).build()
        transformer?.start(mediaItem, output.absolutePath)
    }
    private fun shareNext() {
        if (shareIndex >= clips.size) { status.text="Todos os takes foram encaminhados. Confira a publicação no Instagram."; shareButton.isEnabled=false; return }
        val file=clips[shareIndex]
        val uri=FileProvider.getUriForFile(this,"$packageName.files",file)
        val intent=Intent("com.instagram.share.ADD_TO_STORY").apply {
            setDataAndType(uri,"video/mp4"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); setPackage("com.instagram.android")
        }
        try {
            startActivity(intent)
            shareIndex++
            status.text="Take ${shareIndex}/${clips.size} enviado ao editor do Instagram. Publique e volte para continuar em ordem."
            shareButton.text=if(shareIndex==clips.size) "Concluir sequência" else "Compartilhar take ${shareIndex+1}"
        } catch (e: Exception) {
            status.text="Instagram indisponível. Instale o app ou tente novamente: ${e.message}"
        }
    }
}
