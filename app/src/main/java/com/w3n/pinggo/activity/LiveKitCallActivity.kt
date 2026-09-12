/* Converted to LiveKitCallActivity.java. Kept inert until the IDE releases the file lock.

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonObject
import com.w3n.pinggo.Database.CloudFunction.AppFunction.AppFunctionManager
import com.w3n.pinggo.Database.CloudFunction.Utils.JsonParserUtil
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginStateManager
import com.w3n.pinggo.call.ActiveCallRegistry
import com.w3n.pinggo.data.repository.ChatRepository
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.renderer.SurfaceViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** LiveKit call surface shared by direct and group audio/video rooms. */
class LiveKitCallActivity : AppCompatActivity(), ChatRepository.CallEventListener {
  companion object {
    const val EXTRA_MEDIA_TYPE = "com.w3n.pinggo.EXTRA_LIVEKIT_MEDIA_TYPE"
    const val EXTRA_INCOMING = "com.w3n.pinggo.EXTRA_LIVEKIT_INCOMING"
  }

  private lateinit var room: Room
  private lateinit var status: TextView
  private lateinit var grid: GridLayout
  private lateinit var mute: Button
  private lateinit var camera: Button
  private val renderers = linkedMapOf<String, Pair<VideoTrack, SurfaceViewRenderer>>()
  private var eventsJob: Job? = null
  private var connected = false
  private var muted = false
  private var cameraEnabled = true
  private val mediaType get() = intent.getStringExtra(EXTRA_MEDIA_TYPE) ?: "audio"
  private val incoming get() = intent.getBooleanExtra(EXTRA_INCOMING, false)
  private val callId get() = intent.getStringExtra(VoiceCallActivity.EXTRA_CALL_ID).orEmpty()
  private val chatId get() = intent.getStringExtra(VoiceCallActivity.EXTRA_CALL_CHAT_ID).orEmpty()
  private val peerId get() = intent.getStringExtra(VoiceCallActivity.EXTRA_CALLER_ID).orEmpty()

  private val permissions = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { result ->
    if (result.values.all { it }) authorizeAndConnect()
    else finishWithError("Microphone${if (mediaType == "video") " and camera" else ""} permission required.")
  }

  override fun onCreate(state: Bundle?) {
    super.onCreate(state)
    buildUi()
    room = LiveKit.create(applicationContext)
    ChatRepository.getInstance(this).setCallEventListener(this)
    ActiveCallRegistry.getInstance().register(this, chatId,
      if (mediaType == "video") ActiveCallRegistry.TYPE_VIDEO else ActiveCallRegistry.TYPE_VOICE)
    if (incoming && !intent.getBooleanExtra(VoiceCallActivity.EXTRA_AUTO_ACCEPT, false)) {
      status.text = "Incoming ${if (mediaType == "video") "video" else "voice"} call"
      showIncomingControls()
      sendControl("call_ringing")
    } else requestPermissions()
  }

  private fun buildUi() {
    val root = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
      setPadding(24, 48, 24, 32); setBackgroundColor(Color.rgb(16, 24, 32))
    }
    status = TextView(this).apply { setTextColor(Color.WHITE); textSize = 18f; text = "Preparing call…" }
    grid = GridLayout(this).apply { columnCount = 2 }
    val controls = LinearLayout(this).apply { gravity = Gravity.CENTER }
    mute = Button(this).apply { text = "Mute"; setOnClickListener { toggleMute() } }
    camera = Button(this).apply {
      text = "Camera off"; visibility = if (mediaType == "video") android.view.View.VISIBLE else android.view.View.GONE
      setOnClickListener { toggleCamera() }
    }
    val end = Button(this).apply { text = "End"; setOnClickListener { hangup() } }
    controls.addView(mute); controls.addView(camera); controls.addView(end)
    root.addView(status)
    root.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    root.addView(controls)
    setContentView(root)
  }

  private fun showIncomingControls() {
    val accept = Button(this).apply { text = "Accept"; setOnClickListener {
      (parent as? ViewGroup)?.removeView(this); requestPermissions()
    } }
    val reject = Button(this).apply { text = "Reject"; setOnClickListener {
      sendControl("call_reject"); finish()
    } }
    (status.parent as ViewGroup).addView(LinearLayout(this).apply { addView(accept); addView(reject) }, 1)
  }

  private fun requestPermissions() {
    val required = mutableListOf(Manifest.permission.RECORD_AUDIO)
    if (mediaType == "video") required += Manifest.permission.CAMERA
    val missing = required.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
    if (missing.isEmpty()) authorizeAndConnect() else permissions.launch(missing.toTypedArray())
  }

  private fun authorizeAndConnect() {
    status.text = "Authorizing…"
    AppFunctionManager.getInstance().getLiveKitToken(callId, chatId, mediaType,
      object : AppFunctionManager.Callback {
        override fun onSuccess(value: Any?) {
          val json = value as? JsonObject ?: return finishWithError("Invalid LiveKit response.")
          connect(JsonParserUtil.getString(json, "serverUrl"), JsonParserUtil.getString(json, "participantToken"))
        }
        override fun onError(message: String?) = finishWithError(message ?: "LiveKit authorization failed.")
      })
  }

  private fun connect(url: String, token: String) {
    eventsJob = lifecycleScope.launch {
      launch { room.events.collect { handleRoomEvent(it) } }
      try {
        status.text = "Connecting…"
        room.connect(url, token)
        room.localParticipant.setMicrophoneEnabled(true)
        if (mediaType == "video") {
          room.localParticipant.setCameraEnabled(true)
          attachLocalCamera()
        }
        connected = true
        if (incoming) sendControl("call_answer") else sendControl("call_invite")
        status.text = if (room.remoteParticipants.isEmpty()) "Ringing…" else "Connected"
      } catch (error: Exception) {
        finishWithError(error.message ?: "Could not connect to LiveKit.")
      }
    }
  }

  private fun handleRoomEvent(event: RoomEvent) {
    when (event) {
      is RoomEvent.TrackSubscribed -> if (event.track is VideoTrack)
        attachRemote(participantKey(event.participant.identity?.value, event.participant),
          event.track as VideoTrack)
      is RoomEvent.TrackUnsubscribed ->
        detachRemote(participantKey(event.participant.identity?.value, event.participant))
      is RoomEvent.ParticipantConnected -> status.text = "Connected (${room.remoteParticipants.size + 1})"
      is RoomEvent.ParticipantDisconnected -> {
        detachRemote(participantKey(event.participant.identity?.value, event.participant))
        status.text = "Connected (${room.remoteParticipants.size + 1})"
      }
      is RoomEvent.Reconnecting -> status.text = "Reconnecting…"
      is RoomEvent.Reconnected -> status.text = "Connected"
      is RoomEvent.Disconnected -> if (!isFinishing) finish()
      else -> Unit
    }
  }

  private fun participantKey(identity: String?, participant: Any): String =
    identity?.takeIf { it.isNotBlank() } ?: "participant_${participant.hashCode()}"

  private fun attachLocalCamera() {
    val track = room.localParticipant.getTrackPublication(Track.Source.CAMERA)?.track as? LocalVideoTrack ?: return
    attachVideo("local", track)
  }

  private fun attachRemote(identity: String, track: VideoTrack) = attachVideo(identity, track)

  private fun attachVideo(identity: String, track: VideoTrack) {
    detachRemote(identity)
    val renderer = SurfaceViewRenderer(this)
    room.initVideoRenderer(renderer)
    track.addRenderer(renderer)
    renderers[identity] = track to renderer
    grid.addView(renderer, ViewGroup.LayoutParams(resources.displayMetrics.widthPixels / 2,
      resources.displayMetrics.widthPixels * 2 / 3))
  }

  private fun detachRemote(identity: String) {
    renderers.remove(identity)?.let { (track, renderer) ->
      track.removeRenderer(renderer); grid.removeView(renderer); renderer.release()
    }
  }

  private fun toggleMute() = lifecycleScope.launch {
    muted = !muted; room.localParticipant.setMicrophoneEnabled(!muted); mute.text = if (muted) "Unmute" else "Mute"
  }

  private fun toggleCamera() = lifecycleScope.launch {
    cameraEnabled = !cameraEnabled; room.localParticipant.setCameraEnabled(cameraEnabled)
    camera.text = if (cameraEnabled) "Camera off" else "Camera on"
  }

  private fun sendControl(type: String) {
    val event = JsonObject().apply {
      addProperty("type", type); addProperty("engine", "livekit"); addProperty("callId", callId)
      addProperty("chatId", chatId); addProperty("senderId", LoginStateManager.getInstance().getUID(this@LiveKitCallActivity))
      addProperty("receiverId", peerId); addProperty("mediaType", mediaType)
    }
    ChatRepository.getInstance(this).sendCallEvent(event)
  }

  private fun hangup() {
    sendControl(if (chatId.startsWith("grp_")) "call_leave" else "call_end")
    finish()
  }

  override fun onCallEvent(event: JsonObject) {
    if (JsonParserUtil.getString(event, "callId") != callId) return
    when (JsonParserUtil.getString(event, "type")) {
      "call_ringing" -> runOnUiThread { status.text = "Ringing…" }
      "call_answer" -> runOnUiThread { status.text = "Connected" }
      "call_reject", "call_busy", "call_no_answer", "call_end" -> runOnUiThread { finish() }
    }
  }

  private fun finishWithError(message: String) = runOnUiThread {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show(); finish()
  }

  override fun onDestroy() {
    eventsJob?.cancel()
    renderers.keys.toList().forEach(::detachRemote)
    if (::room.isInitialized) room.disconnect()
    ChatRepository.getInstance(this).clearCallEventListener(this)
    ActiveCallRegistry.getInstance().clear(this)
    super.onDestroy()
  }
}
*/
