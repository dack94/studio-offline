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
import java.text.Normalizer
import java.util.Locale

class MainActivity : Activity() {
 private lateinit var questions: JSONArray
 private var index = 0
 private lateinit var status: TextView
 private lateinit var title: TextView
 private lateinit var answer: EditText
 private lateinit var result: TextView
 private var recognizer: SpeechRecognizer? = null
 private val prefs by lazy { getSharedPreferences("progress", MODE_PRIVATE) }

 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  questions = JSONArray(assets.open("questions.json").bufferedReader().use { it.readText() })
  val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(24,30,24,24); setBackgroundColor(Color.rgb(247,245,240)) }
  root.addView(TextView(this).apply { text="STUDIO  /  OFFLINE"; textSize=14f; setTextColor(Color.rgb(95,109,91)); typeface=Typeface.DEFAULT_BOLD })
  status=TextView(this).apply { textSize=14f; setPadding(0,20,0,20) }; root.addView(status)
  title=TextView(this).apply { textSize=24f; setTextColor(Color.rgb(36,42,36)); typeface=Typeface.DEFAULT_BOLD; setPadding(0,10,0,24) };root.addView(title)
  answer=EditText(this).apply { hint="Scrivi la risposta oppure usa il microfono"; minLines=4; maxLines=8; gravity=Gravity.TOP; setBackgroundColor(Color.WHITE); setPadding(16,16,16,16) };root.addView(answer)
  val buttons=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
  fun btn(label:String, action:()->Unit) { buttons.addView(Button(this).apply { text=label; setOnClickListener { action() } }, LinearLayout.LayoutParams(0,-2,1f)) }
  btn("Parla") { startSpeech() }; btn("Valuta") { evaluate() }; btn("Avanti") { index=(index+1)%questions.length();render() }
  root.addView(buttons)
  result=TextView(this).apply { textSize=16f; setPadding(0,22,0,12); setTextColor(Color.rgb(40,48,41)) };root.addView(result)
  setContentView(ScrollView(this).apply { addView(root) })
  if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),100)
  render()
 }
 private fun render() {
  val q=questions.getJSONObject(index)
  title.text=q.getString("question")
  answer.setText("");result.text=""
  val due=prefs.getLong("due_$index",0L)
  status.text="Domanda ${index+1}/${questions.length()} · ${if(due>0 && due<=System.currentTimeMillis()) "Ripasso in scadenza" else "Allenamento"}"
 }
 private fun norm(s:String):String=Normalizer.normalize(s.lowercase(Locale.ITALIAN),Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"),"").replace(Regex("[^a-z0-9]+")," ").trim()
 private fun evaluate() {
  val response=" "+norm(answer.text.toString())+" "
  if(response.trim().isEmpty()){Toast.makeText(this,"Inserisci una risposta",Toast.LENGTH_SHORT).show();return}
  val concepts=questions.getJSONObject(index).getJSONArray("concepts")
  val found=mutableListOf<String>();val missing=mutableListOf<String>()
  for(i in 0 until concepts.length()) {
   val c=concepts.getJSONObject(i);val alternatives=c.getJSONArray("alternatives")
   val hit=(0 until alternatives.length()).any { response.contains(" "+norm(alternatives.getString(it))+" ") }
   if(hit) found.add(c.getString("label")) else missing.add(c.getString("label"))
  }
  val score=(100.0*found.size/concepts.length()).toInt()
  result.text="Punteggio indicativo: $score%\n\nRilevati: ${found.joinToString().ifEmpty { "nessuno" }}\n\nMancanti: ${missing.joinToString().ifEmpty { "nessuno" }}\n\nATTENZIONE: valutazione lessicale, NON semantica. Negazioni e relazioni errate possono essere valutate male."
  val days=when {score>=90->7;score>=60->3;else->1}
  prefs.edit().putLong("due_$index",System.currentTimeMillis()+days*86400000L).putInt("score_$index",score).apply()
  scheduleReminder()
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
 override fun onDestroy(){recognizer?.destroy();super.onDestroy()}
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
