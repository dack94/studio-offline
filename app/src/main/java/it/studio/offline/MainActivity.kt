package it.studio.offline

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.*
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.widget.*
import org.json.JSONArray
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File
import java.net.URL
import java.util.concurrent.Executors
import java.text.Normalizer
import java.util.Locale
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.json.JSONObject

class MainActivity : Activity() {
 private lateinit var questions: JSONArray
 private val importPackCode = 202
 private val importPdfCode = 203
 private var passages=JSONArray()
 private var importedPack = false
 private var index = 0
 private lateinit var status: TextView
 private lateinit var title: TextView
 private lateinit var answer: EditText
 private lateinit var result: TextView
 private var recognizer: SpeechRecognizer? = null
 private val worker = Executors.newSingleThreadExecutor()
 private var model: LlmInference? = null
 private val modelFile by lazy { File(filesDir,"qwen2.5-0.5b-q8.task") }
 private val modelUrl = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task?download=true"
 private val prefs by lazy { getSharedPreferences("progress", MODE_PRIVATE) }

 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  PDFBoxResourceLoader.init(applicationContext)
  questions = loadPack()
  val saved=File(filesDir,"passages.json")
  if(saved.exists()) try { passages=JSONArray(saved.readText()) }catch(_:Exception){}
  val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(24,30,24,24); setBackgroundColor(Color.rgb(247,245,240)) }
  root.addView(TextView(this).apply { text="STUDIO  /  OFFLINE"; textSize=14f; setTextColor(Color.rgb(95,109,91)); typeface=Typeface.DEFAULT_BOLD })
  status=TextView(this).apply { textSize=14f; setPadding(0,20,0,20) }; root.addView(status)
  title=TextView(this).apply { textSize=24f; setTextColor(Color.rgb(36,42,36)); typeface=Typeface.DEFAULT_BOLD; setPadding(0,10,0,24) };root.addView(title)
  answer=EditText(this).apply { hint="Scrivi la risposta oppure usa il microfono"; minLines=4; maxLines=8; gravity=Gravity.TOP; setBackgroundColor(Color.WHITE); setPadding(16,16,16,16) };root.addView(answer)
  val buttons=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
  fun btn(label:String, action:()->Unit) { buttons.addView(Button(this).apply { text=label; setOnClickListener { action() } }, LinearLayout.LayoutParams(0,-2,1f)) }
  btn("Parla") { startSpeech() }; btn("Valuta") { evaluate() }; btn("Avanti") { index=(index+1)%questions.length();render() }
  root.addView(buttons)
  root.addView(Button(this).apply { text="Importa PDF";setOnClickListener { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="application/pdf";addCategory(Intent.CATEGORY_OPENABLE) },importPdfCode) } })
  root.addView(Button(this).apply { text="Genera domande dal PDF (bozze)";setOnClickListener { generateDrafts() } })
  root.addView(Button(this).apply { text="Rivedi e approva bozze";setOnClickListener { reviewDrafts() } })
  root.addView(Button(this).apply { text="Mostra riferimento";setOnClickListener { val q=questions.getJSONObject(index);AlertDialog.Builder(this@MainActivity).setTitle("Fonte").setMessage(q.optString("reference").ifBlank { q.optJSONArray("concepts")?.let { arr -> (0 until arr.length()).joinToString("; ") { arr.getJSONObject(it).getString("label") } } ?: "Riferimento non disponibile" }+"\n"+q.optString("source","")).setPositiveButton("Chiudi",null).show() } })
  root.addView(Button(this).apply { text="Importa pacchetto di studio verificato (JSON)"; setOnClickListener { val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="application/json"; addCategory(Intent.CATEGORY_OPENABLE) };startActivityForResult(intent,importPackCode) } })
  root.addView(TextView(this).apply { text="Le domande predefinite si possono valutare con Qwen usando la chiave di risposta integrata; non sono ricavate dai tuoi PDF. La valutazione è indicativa, verifica sempre la correttezza.";textSize=13f })
  root.addView(Button(this).apply { text="Scarica Qwen 2.5 0.5B (547 MB, solo una volta)"; setOnClickListener { downloadModel() } })
  result=TextView(this).apply { textSize=16f; setPadding(0,22,0,12); setTextColor(Color.rgb(40,48,41)) };root.addView(result)
  setContentView(ScrollView(this).apply { addView(root) })
  if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),100)
  render()
 }
 private fun loadPack(): JSONArray {
  val saved=File(filesDir,"study-pack.json")
  if(saved.exists()) try {
   val parsed=JSONArray(saved.readText())
   if(parsed.length()>0) { importedPack=true;return parsed }
  }catch(_:Exception){}
  importedPack=false
  return JSONArray(assets.open("questions.json").bufferedReader().use { it.readText() })
 }
 @Deprecated("Used for Android document picker result")
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
  super.onActivityResult(requestCode,resultCode,data)
  if(resultCode!=RESULT_OK) return
  if(requestCode==importPdfCode){importPdf(data);return}
  if(requestCode!=importPackCode)return
  try {
   val uri=data?.data ?: error("File non selezionato")
   val raw=contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("File non leggibile")
   val pack=JSONArray(raw)
   require(pack.length()>0){"Pacchetto vuoto"}
   for(i in 0 until pack.length()) {
    val q=pack.getJSONObject(i)
    require(q.getString("question").isNotBlank() && q.getString("reference").isNotBlank() && q.getString("source").isNotBlank()){"Domanda ${i+1}: mancano question, reference o source"}
   }
   File(filesDir,"study-pack.json").writeText(raw)
   questions=pack;importedPack=true;index=0;render()
   status.text="Pacchetto importato: ${pack.length()} domande. Controlla che le fonti siano corrette."
  }catch(e:Exception){status.text="Importazione non riuscita: ${e.message}"}
 }

 private fun importPdf(data:Intent?) {
  val uri=data?.data?:return
  status.text="Estrazione del testo PDF in corso…"
  worker.execute {
   try {
    val tmp=File(cacheDir,"import.pdf")
    contentResolver.openInputStream(uri)?.use { it.copyTo(tmp.outputStream()) }?:error("PDF non leggibile")
    val chunks=JSONArray()
    PDDocument.load(tmp).use { doc ->
     require(!doc.isEncrypted){"PDF protetto"}
     val stripper=PDFTextStripper()
     for(page in 1..doc.numberOfPages) {
      stripper.startPage=page;stripper.endPage=page
      val pageText=stripper.getText(doc).replace(Regex("\\s+")," ").trim()
      var offset=0
      while(offset<pageText.length) {
       chunks.put(JSONObject().put("text",pageText.substring(offset,(offset+1100).coerceAtMost(pageText.length))).put("source",uri.lastPathSegment?:"PDF").put("page",page))
       offset+=1100
      }
     }
    }
    require(chunks.length()>0){"Nessun testo estraibile: i PDF scannerizzati richiedono OCR"}
    for(i in 0 until chunks.length())passages.put(chunks.getJSONObject(i))
    File(filesDir,"passages.json").writeText(passages.toString())
    tmp.delete()
    runOnUiThread { status.text="PDF indicizzato offline: ${chunks.length()} passaggi. Genera le domande e verifica le bozze." }
   }catch(e:Exception){runOnUiThread {status.text="Errore PDF: ${e.message}"}}
  }
 }
 private fun generateDrafts() {
  if(passages.length()==0){status.text="Importa prima un PDF";return}
  if(!modelFile.exists()){status.text="Scarica prima il modello IA";return}
  status.text="Generazione bozze in corso…"
  worker.execute {
   try {
    if(model==null)model=LlmInference.createFromOptions(this,LlmInference.LlmInferenceOptions.builder().setModelPath(modelFile.absolutePath).setMaxTokens(1024).setMaxTopK(10).build())
    val draft=JSONArray()
    for(i in 0 until passages.length().coerceAtMost(8)){
     val p=passages.getJSONObject(i)
     val prompt="Crea UNA domanda e risposta ESCLUSIVAMENTE da questo testo. Scrivi due righe: DOMANDA: ... e RISPOSTA: ...\\n"+p.getString("text")
     val output=model!!.generateResponse(prompt)
     val q=Regex("DOMANDA:([^\\n]+)").find(output)?.groupValues?.get(1)?.trim()
     val a=Regex("RISPOSTA:([^\\n]+)").find(output)?.groupValues?.get(1)?.trim()
     if(!q.isNullOrBlank()&&!a.isNullOrBlank())draft.put(JSONObject().put("question",q).put("reference",a).put("source",p.getString("source")+" pagina "+p.getInt("page")).put("verified",false))
    }
    File(filesDir,"draft-pack.json").writeText(draft.toString())
    runOnUiThread {status.text="Create ${draft.length()} bozze NON verificate. Non sono abilitate alla valutazione: occorre controllarle con gli appunti."}
   }catch(e:Exception){runOnUiThread {status.text="Generazione fallita: ${e.message}"}}
  }
 }

 private fun reviewDrafts() {
  val file=File(filesDir,"draft-pack.json")
  if(!file.exists()){status.text="Non ci sono bozze. Importa un PDF e genera domande.";return}
  val drafts=try{JSONArray(file.readText())}catch(e:Exception){status.text="Bozze non leggibili";return}
  if(drafts.length()==0){status.text="Nessuna bozza da rivedere";return}
  fun reviewAt(i:Int) {
   if(i>=drafts.length()) {status.text="Revisione completata";return}
   val q=drafts.getJSONObject(i)
   val editor=EditText(this).apply {setText(q.getString("reference"));minLines=3;maxLines=8}
   AlertDialog.Builder(this).setTitle("Verifica risposta ${i+1}/${drafts.length()}")
    .setMessage(q.getString("question")+"\\nFonte: "+q.getString("source")+"\\nControlla sul PDF originale prima di approvare.")
    .setView(editor)
    .setPositiveButton("Approva") {_,_->
     val corrected=editor.text.toString().trim()
     if(corrected.isNotBlank()){
      val saved=File(filesDir,"study-pack.json")
      val pack=if(saved.exists())try{JSONArray(saved.readText())}catch(_:Exception){JSONArray()}else JSONArray()
      pack.put(JSONObject().put("question",q.getString("question")).put("reference",corrected).put("source",q.getString("source")).put("verified",true))
      saved.writeText(pack.toString());questions=pack;importedPack=true;index=0;render()
     }
     reviewAt(i+1)
    }.setNegativeButton("Scarta"){_,_->reviewAt(i+1)}
    .setNeutralButton("Interrompi",null).show()
  }
  reviewAt(0)
 }
 private fun render() {
  val q=questions.getJSONObject(index)
  title.text=q.getString("question")
  answer.setText("");result.text=""
  val due=prefs.getLong("due_$index",0L)
  status.text="Domanda ${index+1}/${questions.length()} · ${if(importedPack) "Pacchetto importato" else "Demo · chiave integrata"} · ${if(due>0 && due<=System.currentTimeMillis()) "Ripasso in scadenza" else "Allenamento"}"
 }
 private fun norm(s:String):String=Normalizer.normalize(s.lowercase(Locale.ITALIAN),Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"),"").replace(Regex("[^a-z0-9]+")," ").trim()
 private fun downloadModel() {
  if(modelFile.exists() && modelFile.length()>520_000_000L) { status.text="Modello già presente. Pronto per valutare.";return }
  status.text="Scaricamento IA in corso (circa 547 MB)…"
  worker.execute {
   val tmp=File(filesDir,"model.tmp")
   try {
    val conn=URL(modelUrl).openConnection().apply { connectTimeout=30000; readTimeout=60000 }
    conn.getInputStream().use { input -> tmp.outputStream().use { output ->
     val buf=ByteArray(65536);var total=0L;var read:Int
     while(input.read(buf).also { read=it }!=-1) {
      output.write(buf,0,read);total+=read
      if(total%(20L*1024*1024)<65536) runOnUiThread { status.text="Modello: ${total/(1024*1024)} MB scaricati" }
     }
    }}
    require(tmp.length()>520_000_000L){"File modello incompleto"}
    require(tmp.renameTo(modelFile)){"Impossibile salvare il modello"}
    runOnUiThread { status.text="Modello scaricato. La valutazione funziona offline." }
   } catch(e:Exception) { tmp.delete();runOnUiThread { status.text="Download non riuscito (${e.javaClass.simpleName}): ${e.message}" } }
  }
 }
 private fun evaluate() {
  val response=answer.text.toString().trim()
  if(response.isEmpty()){Toast.makeText(this,"Inserisci una risposta",Toast.LENGTH_SHORT).show();return}
  val q=questions.getJSONObject(index)

  val reference=if(importedPack)q.getString("reference") else (0 until q.getJSONArray("concepts").length()).joinToString("; ") { q.getJSONArray("concepts").getJSONObject(it).getString("label") }
  val source=if(importedPack)q.getString("source") else "Chiave delle domande dimostrative integrata nell’app (non tratta da PDF)"
  if(!importedPack) {
   val concepts=q.getJSONArray("concepts")
   val recognized=mutableListOf<String>()
   val missing=mutableListOf<String>()
   val normalized=norm(response)
   for(i in 0 until concepts.length()) {
    val concept=concepts.getJSONObject(i)
    val variants=concept.getJSONArray("alternatives")
    val matched=(0 until variants.length()).any { j ->
     val variant=norm(variants.getString(j))
     variant.isNotBlank() && Regex("(^| )"+Regex.escape(variant)+"( |$)").containsMatchIn(normalized)
    }
    if(matched) recognized.add(concept.getString("label")) else missing.add(concept.getString("label"))
   }
   val wrongRenal= index==0 && (normalized.contains("renal") || normalized.contains("surrenal") || normalized.contains("gonadic"))
   val outcome=when {
    recognized.size==concepts.length && !wrongRenal -> "CORRETTA (corrispondenza testuale)"
    wrongRenal -> "ERRATA: hai indicato un ramo viscerale pari, non impari"
    recognized.isNotEmpty() -> "PARZIALE: alcuni concetti attesi non sono stati riconosciuti"
    else -> "ERRATA O NON PERTINENTE: nessun concetto atteso riconosciuto"
   }
   result.text="ESITO: $outcome\\n\\nRICONOSCIUTI: ${recognized.ifEmpty { listOf("nessuno") }.joinToString(", ")}\\nMANCANTI: ${missing.ifEmpty { listOf("nessuno") }.joinToString(", ")}\\n\\nRIFERIMENTO: $reference\\n\\nControllo testuale offline: può non riconoscere parafrasi e sinonimi non presenti nella chiave."
   return
  }
  if(!modelFile.exists()) { result.text="Scarica prima Qwen 2.5. Dopo il download la valutazione è offline.";return }
  val question=q.getString("question")
  val generic=setOf("e cosi che funziona","non lo so","boh","non saprei","ok","ciao","si","no","non ricordo","non ho capito","pai pai","pai pao")
  if(norm(response) in generic || response.length<5) {
   result.text="RISPOSTA NON PERTINENTE\\nLa risposta non contiene elementi anatomici valutabili.\\n\\nRisposta di riferimento: $reference\\nFonte: $source"
   return
  }
  val evidence=(0 until passages.length()).map {passages.getJSONObject(it)}.map { p ->
   val terms=norm(question).split(" ").filter {it.length>4}
   p to terms.count { norm(p.getString("text")).contains(it) }
  }.filter {it.second>0}.sortedByDescending {it.second}.take(2).map {it.first.getString("text").take(700)}
  result.text="L'IA sta valutando la risposta sul tablet…"
  worker.execute {
   try {
    if(model==null) model=LlmInference.createFromOptions(this,
      LlmInference.LlmInferenceOptions.builder().setModelPath(modelFile.absolutePath).setMaxTokens(1024).setMaxTopK(40).build())
    val prompt="""Sei un confrontatore di testi, non un esperto di anatomia. Devi usare ESCLUSIVAMENTE la risposta di riferimento. Non aggiungere alcuna informazione esterna.
DOMANDA: $question
RISPOSTA DI RIFERIMENTO: $reference
FONTE: $source
ESTRATTI DEI PDF (contesto non verificato): ${evidence.joinToString(" | ")}
RISPOSTA DELLO STUDENTE: $response
Confronta il significato, accetta sinonimi e parafrasi SOLO se il significato è inequivocabilmente equivalente. Se la risposta è incomprensibile o ambigua, scrivi NON VALUTABILE. Non inventare correzioni o classificazioni. Non assegnare voti numerici.
Formato obbligatorio:
ESITO: COERENTE / PARZIALE / CONTRADDITTORIA / NON VALUTABILE
PRESENTE: concetti riconoscibili e supportati dalla risposta di riferimento
MANCANTE: concetti richiesti ma non espressi
CONTRADDIZIONI: affermazioni in contrasto con la risposta di riferimento
FONTE: $source
Se non puoi stabilire un confronto attendibile, usa NON VALUTABILE."""
    val evaluation=model!!.generateResponse(prompt)
    val valid=Regex("ESITO:\\s*(COERENTE|PARZIALE|CONTRADDITORIA|NON VALUTABILE)",RegexOption.IGNORE_CASE).containsMatchIn(evaluation)
    runOnUiThread {
     result.text=(if(valid)evaluation else "VALUTAZIONE IA NON DISPONIBILE: Qwen non ha restituito un giudizio strutturato. Non significa che la tua risposta sia sbagliata.\\n\\nRisposta del modello (da verificare): "+evaluation.take(1200))+
      "\\n\\nRisposta di riferimento: $reference\\nFonte: $source\\n\\nVerifica sempre il riferimento: la valutazione automatica può sbagliare."
    }
   }catch(e:Exception) { runOnUiThread { result.text="Errore IA locale: ${e.message}\\nVerifica memoria libera e compatibilità del tablet." } }
  }
 }
 private fun startSpeech() {
  if(Build.VERSION.SDK_INT<31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
   Toast.makeText(this,"Riconoscimento vocale offline non disponibile. Usa la tastiera.",Toast.LENGTH_LONG).show();return
  }
  if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),101);return}
  recognizer?.destroy()
  recognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
  recognizer!!.setRecognitionListener(object:RecognitionListener {
   override fun onReadyForSpeech(params:Bundle?){status.text="Ascolto…"}
   override fun onBeginningOfSpeech(){}
   override fun onRmsChanged(rmsdB:Float){}
   override fun onBufferReceived(buffer:ByteArray?){}
   override fun onEndOfSpeech(){status.text="Elaborazione audio…"}
   override fun onError(error:Int){status.text="Errore riconoscimento ($error). Verifica il pacchetto italiano offline."}
   override fun onResults(results:Bundle?){answer.setText(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?:"");status.text="Trascrizione completata"}
   override fun onPartialResults(partialResults:Bundle?){}
   override fun onEvent(eventType:Int,params:Bundle?){}
  })
  val intent=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
   putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
   putExtra(RecognizerIntent.EXTRA_LANGUAGE,"it-IT")
   putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true)
  }
  recognizer!!.startListening(intent)
 }
 private fun scheduleReminder() {
  val earliest=(0 until questions.length()).map {prefs.getLong("due_$it",0L)}.filter {it>0}.minOrNull()?:return
  val pi=PendingIntent.getBroadcast(this,0,Intent(this,ReminderReceiver::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  (getSystemService(ALARM_SERVICE) as AlarmManager).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,earliest,pi)
 }
 override fun onDestroy(){recognizer?.destroy();worker.shutdown();model?.close();super.onDestroy()}
}

class ReminderReceiver: BroadcastReceiver() {
 override fun onReceive(context:Context,intent:Intent) {
  val manager=context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
  if(Build.VERSION.SDK_INT>=26) manager.createNotificationChannel(NotificationChannel("ripassi","Ripassi",NotificationManager.IMPORTANCE_DEFAULT))
  val open=PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val notification=Notification.Builder(context,"ripassi").setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle("È il momento di ripassare").setContentText("Hai argomenti pronti per una nuova interrogazione.").setContentIntent(open).setAutoCancel(true).build()
  if(Build.VERSION.SDK_INT<33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) manager.notify(10,notification)
 }
}
