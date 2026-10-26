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

class MainActivity : Activity() {
 private lateinit var questions: JSONArray
 private val importPackCode = 202
 private var importedPack = false
 private var index = 0
 private lateinit var status: TextView
 private lateinit var title: TextView
 private lateinit var answer: EditText
 private lateinit var result: TextView
 private var recognizer: SpeechRecognizer? = null
 private val worker = Executors.newSingleThreadExecutor()
 private var model: LlmInference? = null
 private val modelFile by lazy { File(filesDir,"gemma3-1b-it.task") }
 private val modelUrl = "https://huggingface.co/typosbro/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task?download=true"
 private val prefs by lazy { getSharedPreferences("progress", MODE_PRIVATE) }

 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  questions = loadPack()
  val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(24,30,24,24); setBackgroundColor(Color.rgb(247,245,240)) }
  root.addView(TextView(this).apply { text="STUDIO  /  OFFLINE"; textSize=14f; setTextColor(Color.rgb(95,109,91)); typeface=Typeface.DEFAULT_BOLD })
  status=TextView(this).apply { textSize=14f; setPadding(0,20,0,20) }; root.addView(status)
  title=TextView(this).apply { textSize=24f; setTextColor(Color.rgb(36,42,36)); typeface=Typeface.DEFAULT_BOLD; setPadding(0,10,0,24) };root.addView(title)
  answer=EditText(this).apply { hint="Scrivi la risposta oppure usa il microfono"; minLines=4; maxLines=8; gravity=Gravity.TOP; setBackgroundColor(Color.WHITE); setPadding(16,16,16,16) };root.addView(answer)
  val buttons=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
  fun btn(label:String, action:()->Unit) { buttons.addView(Button(this).apply { text=label; setOnClickListener { action() } }, LinearLayout.LayoutParams(0,-2,1f)) }
  btn("Parla") { startSpeech() }; btn("Valuta") { evaluate() }; btn("Avanti") { index=(index+1)%questions.length();render() }
  root.addView(buttons)
  root.addView(Button(this).apply { text="Importa pacchetto di studio verificato (JSON)"; setOnClickListener { val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="application/json"; addCategory(Intent.CATEGORY_OPENABLE) };startActivityForResult(intent,importPackCode) } })
  root.addView(TextView(this).apply { text="Le domande predefinite sono dimostrative. Non sono ricavate dai tuoi PDF. Importa un pacchetto preparato e verificato prima di usare l’app per studiare.";textSize=13f })
  root.addView(Button(this).apply { text="Scarica modello IA (555 MB, solo una volta)"; setOnClickListener { downloadModel() } })
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
  if(requestCode!=importPackCode || resultCode!=RESULT_OK) return
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
 private fun render() {
  val q=questions.getJSONObject(index)
  title.text=q.getString("question")
  answer.setText("");result.text=""
  val due=prefs.getLong("due_$index",0L)
  status.text="Domanda ${index+1}/${questions.length()} · ${if(importedPack) "Pacchetto importato" else "Demo non verificata"} · ${if(due>0 && due<=System.currentTimeMillis()) "Ripasso in scadenza" else "Allenamento"}"
 }
 private fun norm(s:String):String=Normalizer.normalize(s.lowercase(Locale.ITALIAN),Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"),"").replace(Regex("[^a-z0-9]+")," ").trim()
 private fun downloadModel() {
  if(modelFile.exists() && modelFile.length()>500_000_000L) { status.text="Modello già presente. Pronto per valutare.";return }
  status.text="Scaricamento IA in corso (circa 555 MB)…"
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
    require(tmp.length()>500_000_000L){"File modello incompleto"}
    require(tmp.renameTo(modelFile)){"Impossibile salvare il modello"}
    runOnUiThread { status.text="Modello scaricato. La valutazione funziona offline." }
   } catch(e:Exception) { tmp.delete();runOnUiThread { status.text="Download non riuscito (${e.javaClass.simpleName}): ${e.message}" } }
  }
 }
 private fun evaluate() {
  val response=answer.text.toString().trim()
  if(response.isEmpty()){Toast.makeText(this,"Inserisci una risposta",Toast.LENGTH_SHORT).show();return}
  if(!modelFile.exists()) { result.text="Scarica prima il modello IA. Dopo il download la valutazione è offline.";return }
  val q=questions.getJSONObject(index)
  if(!importedPack){result.text="Valutazione disabilitata per le domande demo: importa un pacchetto con risposte e fonti verificate.";return}
  val reference=q.getString("reference")
  val source=q.getString("source")
  val question=q.getString("question")
  result.text="L'IA sta valutando la risposta sul tablet…"
  worker.execute {
   try {
    if(model==null) model=LlmInference.createFromOptions(this,
      LlmInference.LlmInferenceOptions.builder().setModelPath(modelFile.absolutePath).setMaxTokens(1024).setMaxTopK(40).build())
    val prompt="""Sei un confrontatore di testi, non un esperto di anatomia. Devi usare ESCLUSIVAMENTE la risposta di riferimento. Non aggiungere alcuna informazione esterna.
DOMANDA: $question
RISPOSTA DI RIFERIMENTO: $reference
FONTE: $source
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
    runOnUiThread { result.text=evaluation+"\\n\\nRiscontro automatico non certificato: verifica sempre il testo della fonte. Nessun voto viene attribuito automaticamente." }
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
