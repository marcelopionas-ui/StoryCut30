package com.storycut.app

import android.content.*
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.*
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.media3.common.util.UnstableApi
import java.io.File

@UnstableApi
class MainActivity : ComponentActivity() {
    private var source: Uri? = null
    private var duration = 0L
    private lateinit var seconds: EditText
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var readyText: TextView
    private lateinit var takeText: TextView
    private lateinit var startButton: Button
    private lateinit var shareButton: Button
    private lateinit var prevButton: Button
    private lateinit var nextButton: Button
    private lateinit var confirmButton: Button
    private lateinit var retryButton: Button
    private lateinit var cleanButton: Button
    private lateinit var whenShare: RadioGroup
    private lateinit var account: RadioGroup
    private lateinit var tags: EditText
    private lateinit var tagMode: Spinner
    private var selected = 0
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences(CutService.PREFS, MODE_PRIVATE) }
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            source = uri
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
            val r = MediaMetadataRetriever()
            try { r.setDataSource(this, uri); duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                status.text = "Vídeo selecionado: ${duration / 1000}s"
            } catch (e: Exception) { status.text = "Erro ao abrir: ${e.message}" } finally { r.release() }
        }
    }
    private val refresh = object : Runnable { override fun run() { render(); handler.postDelayed(this, 750) } }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selected = prefs.getInt("selected", 0)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 32, 28, 32) }
        fun label(s: String) = TextView(this).apply { text = s; textSize = 18f; setPadding(0, 12, 0, 8) }
        root.addView(label("StoryCut • Seus Stories em ordem"))
        root.addView(Button(this).apply { text = "Selecionar vídeo"; setOnClickListener { picker.launch(arrayOf("video/*")) } })
        root.addView(label("Duração de cada take (segundos)"))
        seconds = EditText(this).apply { inputType = 2; setText("30") }; root.addView(seconds)
        root.addView(label("Conta do Instagram"))
        account = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        account.addView(RadioButton(this).apply { id = 101; text = "Pessoal (compartilhamento assistido)"; isChecked = true })
        account.addView(RadioButton(this).apply { id = 102; text = "Empresarial (API ainda não configurada)" })
        root.addView(account)
        root.addView(label("Quando liberar o compartilhamento?"))
        whenShare = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        whenShare.addView(RadioButton(this).apply { id = 201; text = "Assim que cada take ficar pronto"; isChecked = true })
        whenShare.addView(RadioButton(this).apply { id = 202; text = "Somente após terminar todos" })
        root.addView(whenShare)
        root.addView(label("Marcações para copiar no Instagram"))
        tagMode = Spinner(this)
        tagMode.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            listOf("Mesmos perfis em todos", "Decidir por take", "Sem marcações"))
        root.addView(tagMode)
        tags = EditText(this).apply { hint = "@perfil1 @perfil2" }; root.addView(tags)
        startButton = Button(this).apply { text = "Iniciar processamento"; setOnClickListener { begin() } }; root.addView(startButton)
        status = label("Selecione um vídeo para começar"); root.addView(status)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }; root.addView(progress)
        readyText = label(""); root.addView(readyText)
        takeText = label(""); root.addView(takeText)
        prevButton = Button(this).apply { text = "← Voltar take"; setOnClickListener { selected = (selected - 1).coerceAtLeast(0); render() } }; root.addView(prevButton)
        nextButton = Button(this).apply { text = "Próximo take →"; setOnClickListener { selected++; render() } }; root.addView(nextButton)
        shareButton = Button(this).apply { text = "Abrir take no Instagram"; setOnClickListener { share() } }; root.addView(shareButton)
        confirmButton = Button(this).apply { text = "✓ Confirmar que publiquei"; setOnClickListener { setState("publicado"); selected++; render() } }; root.addView(confirmButton)
        retryButton = Button(this).apply { text = "Marcar para reenviar"; setOnClickListener { setState("reenviar"); render() } }; root.addView(retryButton)
        root.addView(Button(this).apply { text = "Copiar perfis para marcação"; setOnClickListener {
            val text = if (tagMode.selectedItemPosition == 2) "" else tags.text.toString()
            (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(ClipData.newPlainText("Marcações", text))
            Toast.makeText(this@MainActivity, "Perfis copiados", Toast.LENGTH_SHORT).show()
        } })
        cleanButton = Button(this).apply { text = "Excluir takes gerados"; setOnClickListener { askCleanup() } }; root.addView(cleanButton)
        setContentView(ScrollView(this).apply { addView(root) })
    }
    override fun onResume() { super.onResume(); handler.removeCallbacks(refresh); handler.post(refresh) }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }
    private fun begin() {
        val uri = source ?: run { status.text = "Selecione um vídeo"; return }
        val secs = seconds.text.toString().toLongOrNull()
        if (secs == null || secs !in 1..3600 || duration <= 0) { status.text = "Duração inválida (1 a 3600 segundos)"; return }
        if (prefs.getBoolean("running", false)) { status.text = "Já existe um processamento ativo"; return }
        selected = 0
        val intent = Intent(this, CutService::class.java).apply {
            action = CutService.ACTION_START; putExtra("uri", uri.toString()); putExtra("step", secs * 1000); putExtra("total", duration)
        }
        ContextCompat.startForegroundService(this, intent)
    }
    private fun fileForTake(): File? {
        val folder = prefs.getString("folder", null) ?: return null
        return File(folder, "take_${(selected + 1).toString().padStart(4, '0')}.mp4")
    }
    private fun render() {
        val ready = prefs.getInt("ready", 0)
        val total = prefs.getInt("count", 0)
        val pct = prefs.getInt("progress", 0)
        val running = prefs.getBoolean("running", false)
        progress.progress = pct
        readyText.text = "$pct% processado • $ready de $total takes prontos • ${100 - pct}% restante"
        status.text = prefs.getString("error", null) ?: if (running) "Processando em segundo plano" else if (prefs.getBoolean("done", false)) "Processamento concluído" else "Pronto para começar"
        val available = ready > 0 && (whenShare.checkedRadioButtonId == 201 || !running)
        selected = selected.coerceAtLeast(0)
        val state = prefs.getString("take_${selected + 1}", "pendente")
        takeText.text = "Take ${selected + 1} / $total • $state${if (selected >= ready) " • aguardando" else ""}"
        prevButton.isEnabled = selected > 0
        nextButton.isEnabled = selected + 1 < ready && available
        shareButton.isEnabled = available && selected < ready
        confirmButton.isEnabled = available && selected < ready
        retryButton.isEnabled = available && selected < ready
        startButton.isEnabled = !running
        cleanButton.isEnabled = !running && prefs.getString("folder", null) != null
        prefs.edit().putInt("selected", selected).apply()
    }
    private fun setState(value: String) { prefs.edit().putString("take_${selected + 1}", value).apply() }
    private fun share() {
        val file = fileForTake() ?: return
        if (!file.exists()) { status.text = "Take ainda não está disponível"; return }
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val intent = Intent("com.instagram.share.ADD_TO_STORY").apply {
            setDataAndType(uri, "video/mp4"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); setPackage("com.instagram.android")
        }
        try { startActivity(intent); setState("enviado ao editor — não confirmado") }
        catch (e: Exception) { status.text = "Não foi possível abrir Instagram: ${e.message}" }
    }
    private fun askCleanup() {
        AlertDialog.Builder(this).setTitle("Excluir takes gerados?")
            .setMessage("Apenas os arquivos de takes deste processamento serão apagados. O vídeo original será preservado. Confirme que não precisa reenviar nenhum take.")
            .setNegativeButton("Cancelar", null).setPositiveButton("Excluir takes") { _, _ ->
                val folder = prefs.getString("folder", null)?.let(::File)
                if (folder != null && folder.isDirectory) {
                    folder.listFiles()?.filter { it.name.matches(Regex("take_\\d{4,}\\.mp4")) }?.forEach { it.delete() }
                    folder.delete()
                }
                prefs.edit().clear().apply(); selected = 0; render()
            }.show()
    }
}
