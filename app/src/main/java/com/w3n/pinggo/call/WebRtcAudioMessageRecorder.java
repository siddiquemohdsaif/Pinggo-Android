package com.w3n.pinggo.call;

import android.content.Context;
import android.media.MediaRecorder;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.AudioTrackSink;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.audio.JavaAudioDeviceModule;
import org.webrtc.audio.AudioProcessingOptions;
import org.webrtc.audio.AudioProcessingOptionsResult;
import org.webrtc.audio.AudioProcessingState;

/** Records WebRTC-APM-processed microphone audio through an on-device loopback peer. */
public final class WebRtcAudioMessageRecorder {
  private final Context context;
  private final File output;
  private final Object writerLock = new Object();
  private final AtomicInteger maxAmplitude = new AtomicInteger();
  private JavaAudioDeviceModule audioDeviceModule;
  private PeerConnectionFactory factory;
  private PeerConnection sender;
  private PeerConnection receiver;
  private AudioSource audioSource;
  private AudioTrack localTrack;
  private AudioTrack processedTrack;
  private FileOutputStream writer;
  private int sampleRate;
  private int channels;
  private long pcmBytes;
  private volatile boolean recording;

  public WebRtcAudioMessageRecorder(Context context, File output) {
    this.context = context.getApplicationContext();
    this.output = output;
  }

  public void start() throws Exception {
    PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context)
        .setEnableInternalTracer(false).createInitializationOptions());
    audioDeviceModule = JavaAudioDeviceModule.builder(context)
        .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
        .setUseHardwareNoiseSuppressor(true)
        .setUseHardwareAcousticEchoCanceler(true)
        .setUseLowLatency(true)
        .createAudioDeviceModule();
    // This is a capture-only loopback. Never route its remote leg to the speaker,
    // otherwise the user hears their own processed voice and it can be captured again.
    audioDeviceModule.setSpeakerMute(true);
    factory = PeerConnectionFactory.builder()
        .setAudioDeviceModule(audioDeviceModule)
        .createPeerConnectionFactory();
    PeerConnection.RTCConfiguration config = new PeerConnection.RTCConfiguration(
        Collections.emptyList());
    config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
    sender = factory.createPeerConnection(config, senderObserver);
    receiver = factory.createPeerConnection(config, receiverObserver);
    if (sender == null || receiver == null)
      throw new IOException("Unable to create the WebRTC audio recorder.");

    audioSource = factory.createAudioSource(new MediaConstraints());
    localTrack = factory.createAudioTrack("pinggo_message_audio", audioSource);
    AudioProcessingOptionsResult result = localTrack.setAudioProcessingOptions(
        AudioProcessingOptions.communication());
    if (!result.isSuccess())
      throw new IOException("WebRTC APM configuration failed: " + result.code);
    AudioProcessingState processingState = factory.getAudioProcessingState();
    if (!processingState.hasAudioProcessingModule)
      throw new IOException("WebRTC APM is unavailable on this device.");
    localTrack.setEnabled(true);
    sender.addTrack(localTrack, Collections.singletonList("pinggo_message_stream"));

    writer = new FileOutputStream(output);
    writer.write(new byte[44]);
    recording = true;
    sender.createOffer(new SimpleSdpObserver() {
      @Override public void onCreateSuccess(SessionDescription offer) {
        sender.setLocalDescription(new SimpleSdpObserver(), offer);
        receiver.setRemoteDescription(new SimpleSdpObserver() {
          @Override public void onSetSuccess() {
            receiver.createAnswer(new SimpleSdpObserver() {
              @Override public void onCreateSuccess(SessionDescription answer) {
                receiver.setLocalDescription(new SimpleSdpObserver(), answer);
                sender.setRemoteDescription(new SimpleSdpObserver() {
                  @Override public void onSetSuccess() {}
                }, answer);
              }
            }, new MediaConstraints());
          }
        }, offer);
      }
    }, new MediaConstraints());
  }

  public int getMaxAmplitude() {
    return maxAmplitude.getAndSet(0);
  }

  public boolean stop() {
    return close(true);
  }

  public void cancel() {
    close(false);
  }

  private boolean close(boolean keepFile) {
    recording = false;
    if (processedTrack != null) processedTrack.removeSink(processedAudioSink);
    if (sender != null) { sender.close(); sender.dispose(); sender = null; }
    if (receiver != null) { receiver.close(); receiver.dispose(); receiver = null; }
    if (localTrack != null) { localTrack.dispose(); localTrack = null; }
    if (audioSource != null) { audioSource.dispose(); audioSource = null; }
    if (factory != null) { factory.dispose(); factory = null; }
    if (audioDeviceModule != null) { audioDeviceModule.release(); audioDeviceModule = null; }
    synchronized (writerLock) {
      if (writer != null) {
        try { writer.close(); } catch (IOException ignored) {}
        writer = null;
      }
    }
    boolean valid = keepFile && pcmBytes > 0 && sampleRate > 0 && channels > 0;
    if (valid) {
      try { writeWavHeader(); } catch (IOException error) { valid = false; }
    }
    if (!valid && output.exists()) output.delete();
    return valid;
  }

  private final AudioTrackSink processedAudioSink = (audioData, bitsPerSample,
      sampleRate, numberOfChannels, numberOfFrames, absoluteCaptureTimestampMs) -> {
    if (!recording || bitsPerSample != 16) return;
    ByteBuffer data = audioData.duplicate();
    byte[] bytes = new byte[data.remaining()];
    data.get(bytes);
    int peak = 0;
    for (int i = 0; i + 1 < bytes.length; i += 2) {
      int value = (short) ((bytes[i] & 0xff) | (bytes[i + 1] << 8));
      peak = Math.max(peak, Math.abs(value));
    }
    maxAmplitude.accumulateAndGet(peak, Math::max);
    synchronized (writerLock) {
      if (!recording || writer == null) return;
      try {
        this.sampleRate = sampleRate;
        channels = numberOfChannels;
        writer.write(bytes);
        pcmBytes += bytes.length;
      } catch (IOException ignored) {
        recording = false;
      }
    }
  };

  private final PeerConnection.Observer senderObserver = new BasePeerObserver() {
    @Override public void onIceCandidate(IceCandidate candidate) {
      if (receiver != null) receiver.addIceCandidate(candidate);
    }
  };

  private final PeerConnection.Observer receiverObserver = new BasePeerObserver() {
    @Override public void onIceCandidate(IceCandidate candidate) {
      if (sender != null) sender.addIceCandidate(candidate);
    }
    @Override public void onAddTrack(RtpReceiver receiver, MediaStream[] streams) {
      if (receiver.track() instanceof AudioTrack) {
        processedTrack = (AudioTrack) receiver.track();
        processedTrack.setVolume(0.0);
        processedTrack.addSink(processedAudioSink);
      }
    }
  };

  private void writeWavHeader() throws IOException {
    try (RandomAccessFile file = new RandomAccessFile(output, "rw")) {
      int byteRate = sampleRate * channels * 2;
      file.seek(0);
      file.writeBytes("RIFF");
      writeLittleEndian(file, pcmBytes + 36, 4);
      file.writeBytes("WAVEfmt ");
      writeLittleEndian(file, 16, 4);
      writeLittleEndian(file, 1, 2);
      writeLittleEndian(file, channels, 2);
      writeLittleEndian(file, sampleRate, 4);
      writeLittleEndian(file, byteRate, 4);
      writeLittleEndian(file, channels * 2, 2);
      writeLittleEndian(file, 16, 2);
      file.writeBytes("data");
      writeLittleEndian(file, pcmBytes, 4);
    }
  }

  private static void writeLittleEndian(RandomAccessFile file, long value, int bytes)
      throws IOException {
    for (int i = 0; i < bytes; i++) file.write((int) (value >> (8 * i)) & 0xff);
  }

  private static class SimpleSdpObserver implements SdpObserver {
    @Override public void onCreateSuccess(SessionDescription sdp) {}
    @Override public void onSetSuccess() {}
    @Override public void onCreateFailure(String error) {}
    @Override public void onSetFailure(String error) {}
  }

  private abstract static class BasePeerObserver implements PeerConnection.Observer {
    @Override public void onSignalingChange(PeerConnection.SignalingState state) {}
    @Override public void onIceConnectionChange(PeerConnection.IceConnectionState state) {}
    @Override public void onIceConnectionReceivingChange(boolean receiving) {}
    @Override public void onIceGatheringChange(PeerConnection.IceGatheringState state) {}
    @Override public void onIceCandidatesRemoved(IceCandidate[] candidates) {}
    @Override public void onAddStream(MediaStream stream) {}
    @Override public void onRemoveStream(MediaStream stream) {}
    @Override public void onDataChannel(DataChannel channel) {}
    @Override public void onRenegotiationNeeded() {}
    @Override public void onAddTrack(RtpReceiver receiver, MediaStream[] streams) {}
  }
}
