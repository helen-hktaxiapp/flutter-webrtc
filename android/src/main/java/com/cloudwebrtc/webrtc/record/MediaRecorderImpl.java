
package com.cloudwebrtc.webrtc.record;

import androidx.annotation.Nullable;
import android.util.Log;

import com.cloudwebrtc.webrtc.utils.EglUtils;

import org.webrtc.VideoTrack;
import java.io.*;
import java.io.File;

import java.nio.ByteBuffer;
import android.media.AudioRecord;
import android.media.AudioFormat;
import android.media.MediaRecorder.AudioSource;
import java.util.Arrays;
import org.webrtc.CalledByNative;
import java.util.Arrays;
import org.webrtc.audio.JavaAudioDeviceModule.AudioRecordErrorCallback;
import org.webrtc.audio.JavaAudioDeviceModule.AudioRecordStartErrorCode;
import org.webrtc.ThreadUtils;
import org.webrtc.audio.JavaAudioDeviceModule.SamplesReadyCallback;
import android.os.Process;
import java.lang.System;
import java.io.File;
import java.io.FileOutputStream;


public class MediaRecorderImpl {

    private final Integer id;
    private final VideoTrack videoTrack;
    private final AudioSamplesInterceptor audioInterceptor;
    private VideoFileRenderer videoFileRenderer;
    private boolean isRunning = false;
    private File recordFile;
    private @Nullable AudioRecord audioRecord = null;
    private ByteBuffer byteBuffer;
    private byte[] emptyBytes;
    private static final int BUFFER_SIZE_FACTOR = 2;
    // private @Nullable AudioRecordThread audioThread = null;
    private Thread audioThread = null;
    private AudioFileRenderer audioFileRenderer;

    public MediaRecorderImpl(Integer id, @Nullable VideoTrack videoTrack, @Nullable AudioSamplesInterceptor audioInterceptor) {
        this.id = id;
        this.videoTrack = videoTrack;
        this.audioInterceptor = audioInterceptor;
    }

    public void startRecording(File file) throws Exception {
        recordFile = file;
        if (isRunning)
            return;
        isRunning = true;
        //noinspection ResultOfMethodCallIgnored
        file.getParentFile().mkdirs();
        if (videoTrack != null) {
            videoFileRenderer = new VideoFileRenderer(
                file.getAbsolutePath(),
                EglUtils.getRootEglBaseContext(),
                audioInterceptor != null
            );
            videoTrack.addSink(videoFileRenderer);
            if (audioInterceptor != null)
                audioInterceptor.attachCallback(id, videoFileRenderer);
        } else {
            Log.e(TAG, "Video track is null");
            if (audioInterceptor != null) {
                //TODO(rostopira): audio only recording
                // throw new Exception("Audio-only recording not implemented yet");
                Log.d(TAG, "MediaRecorder123");
                // initRecording();
                // startAudioRecord(audioInterceptor);

                Log.d(TAG, "Try to use onWebrtcSamplesReady");
                audioFileRenderer = new AudioFileRenderer(file);
                audioInterceptor.attachCallback(id, audioFileRenderer);
            }
        }
    }
    
    private void initRecording(){
        //REFERENCE TO APPRTCMOBILE GITHUB https://github.com/jianrong-rtc/AppRTCMobile.git
        Log.d(TAG, "initRecording(sampleRate=" + 16000+ ", channels=" + 2 + ")");
        if (audioRecord != null) {
            Log.d(TAG, "InitRecording called twice without StopRecording.");
            return;
        }
        int sampleRate = 16000;
        int channelConfig = AudioFormat.CHANNEL_IN_STEREO;
        final int bytesPerFrame = 2 * (16 / 8);//channels * (BITS_PER_SAMPLE / 8);
        final int framesPerBuffer = 16000 / 100;//sampleRate / BUFFERS_PER_SECOND;
        byteBuffer = ByteBuffer.allocateDirect(bytesPerFrame * framesPerBuffer);
        Log.d(TAG, "byteBuffer.capacity: " + byteBuffer.capacity());
        emptyBytes = new byte[byteBuffer.capacity()];

        // Rather than passing the ByteBuffer with every callback (requiring
        // the potentially expensive GetDirectBufferAddress) we simply have the
        // the native class cache the address to the memory once.

        // nativeCacheDirectBufferAddress(byteBuffer, nativeAudioRecord);
        
        // Get the minimum buffer size required for the successful creation of
        // an AudioRecord object, in byte units.
        // Note that this size doesn't guarantee a smooth recording under load.

        int minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT);
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.d(TAG, "AudioRecord.getMinBufferSize failed: " + minBufferSize);
        return;
        }
        Log.d(TAG, "AudioRecord.getMinBufferSize: " + minBufferSize);
    
        // Use a larger buffer size than the minimum required when creating the
        // AudioRecord instance to ensure smooth recording under load. It has been
        // verified that it does not increase the actual recording latency.

        int bufferSizeInBytes = Math.max(BUFFER_SIZE_FACTOR * minBufferSize, byteBuffer.capacity());
        Log.d(TAG, "bufferSizeInBytes: " + bufferSizeInBytes);
        try {
            audioRecord = new AudioRecord(AudioSource.VOICE_COMMUNICATION, 16000, AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT, bufferSizeInBytes);
        } catch (IllegalArgumentException e) {
            Log.d(TAG, "AudioRecord ctor error: " + e.getMessage());
            releaseAudioResources();
            return;
        }
        if (audioRecord == null || audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.d(TAG, "Failed to create a new AudioRecord instance");
            releaseAudioResources();
            return;
        }
        // if (effects != null) {
        // effects.enable(audioRecord.getAudioSessionId());
        // }
        // logMainParameters();
        // logMainParametersExtended();
        // return framesPerBuffer;
    }

    private boolean startAudioRecord(AudioSamplesInterceptor audioSampleInterceptor) {
        Log.d(TAG, "startRecording");
        assertTrue(audioRecord != null);
        assertTrue(audioThread == null);
        try {
          Log.d(TAG, "audioSampleInterceptor.write()");
        //   audioSampleInterceptor
          audioRecord.startRecording();
        } catch (IllegalStateException e) {
            Log.d(TAG,
              "AudioRecord.startRecording failed: " + e.getMessage());
          return false;
        }
        if (audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
            Log.d(TAG,
              "AudioRecord.startRecording failed - incorrect state :"
              + audioRecord.getRecordingState());
          return false;
        }
        // audioThread = new AudioRecordThread("AudioRecordJavaThread");
        audioThread = new Thread(new RecordingRunnable(), "Recording Thread");
        audioThread.start();
        return true;
    }

    private void releaseAudioResources() {
        Log.d(TAG, "releaseAudioResources");
        if (audioRecord != null) {
          audioRecord.release();
          audioRecord = null;
        }
    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
          throw new AssertionError("Expected condition to be true");
        }
    }

    private native void nativeCacheDirectBufferAddress(ByteBuffer byteBuffer, long nativeAudioRecord);

    public File getRecordFile() { return recordFile; }

    public void stopRecording() {
        Log.d(TAG, "stopRecording");

        isRunning = false;
        if (audioInterceptor != null){
          Log.d(TAG, "audioInterceptor detachCallback but not null");
          audioInterceptor.detachCallback(id);
        }
        if (videoTrack != null && videoFileRenderer != null) {
            videoTrack.removeSink(videoFileRenderer);
            videoFileRenderer.release();
            videoFileRenderer = null;
        }
        stopAudioRecord();
    }

    private boolean stopAudioRecord() {
        Log.d(TAG, "stopRecording");
        assertTrue(audioThread != null);
        audioThread.stop();
        // if (!ThreadUtils.joinUninterruptibly(audioThread, AUDIO_RECORD_THREAD_JOIN_TIMEOUT_MS)) {
        //     Log.e(TAG, "Join of AudioRecordJavaThread timed out");
        //     WebRtcAudioUtils.logAudioState(TAG);
        // }
        audioThread = null;
        // if (effects != null) {
        //   effects.release();
        // }
        releaseAudioResources();
        return true;
    }
    
    private static final String TAG = "MediaRecorderImpl";

    private class AudioRecordThread extends Thread {
        private volatile boolean keepAlive = true;
    
        public AudioRecordThread(String name) {
          super(name);
        }
    
        @Override
        public void run() {
          Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
        //   Log.d(TAG, "AudioRecordThread" + WebRtcAudioUtils.getThreadInfo());
          assertTrue(audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING);
    
          long lastTime = System.nanoTime();
          while (keepAlive) {
            int bytesRead = audioRecord.read(byteBuffer, byteBuffer.capacity());
            if (bytesRead == byteBuffer.capacity()) {
            //   if (microphoneMute) {
            //     byteBuffer.clear();
            //     byteBuffer.put(emptyBytes);
            //   }
              // It's possible we've been shut down during the read, and stopRecording() tried and
              // failed to join this thread. To be a bit safer, try to avoid calling any native methods
              // in case they've been unregistered after stopRecording() returned.
              if (keepAlive) {
                // nativeDataIsRecorded(nativeAudioRecord, bytesRead);
                // Log.d("NativeDataIsRecorded");
              }
              byte[] data = Arrays.copyOfRange(byteBuffer.array(), byteBuffer.arrayOffset(),byteBuffer.capacity() + byteBuffer.arrayOffset());
            //   if (audioSamplesReadyCallback != null) {
                // Copy the entire byte buffer array. The start of the byteBuffer is not necessarily
                // at index 0.
                // byte[] data = Arrays.copyOfRange(byteBuffer.array(), byteBuffer.arrayOffset(),
                //     byteBuffer.capacity() + byteBuffer.arrayOffset());
                // audioSamplesReadyCallback.onWebRtcAudioRecordSamplesReady(
                //     new JavaAudioDeviceModule.AudioSamples(audioRecord.getAudioFormat(),
                //         audioRecord.getChannelCount(), audioRecord.getSampleRate(), data));
            //   }
            } else {
              String errorMessage = "AudioRecord.read failed: " + bytesRead;
              Log.e(TAG, errorMessage);
              if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION) {
                keepAlive = false;
                // Log.d(errorMessage);
              }
            }
          }
    
          try {
            if (audioRecord != null) {
              audioRecord.stop();
            }
          } catch (IllegalStateException e) {
            Log.e(TAG, "AudioRecord.stop failed: " + e.getMessage());
          }
        }
    
        // Stops the inner thread loop and also calls AudioRecord.stop().
        // Does not block the calling thread.
        public void stopThread() {
            Log.d(TAG, "stopThread");
            keepAlive = false;
        }
    }

    private class RecordingRunnable implements Runnable {

      @Override
      public void run() {
        final int bytesPerFrame = 2 * (16 / 8);//channels * (BITS_PER_SAMPLE / 8);
        final int framesPerBuffer = 16000 / 100;//sampleRate / BUFFERS_PER_SECOND;

          final File file = new File("/storage/emulated/0/Android/data/com.cornermation.calltaxi/files/webrtc_android", "recording.mp4");
        //   System.out.println("External pathh" + Environment.getExternalStorageDirectory() );
        //   Log.d("externalpath" + Environment.getExternalStorageDirectory() );
          final ByteBuffer buffer = ByteBuffer.allocateDirect(bytesPerFrame * framesPerBuffer);
          int BUFFER_SIZE = bytesPerFrame * framesPerBuffer;
          try (final FileOutputStream outStream = new FileOutputStream(file)) {
              while (isRunning) {
                  int result = audioRecord.read(buffer, BUFFER_SIZE);
                  if (result < 0) {
                      throw new RuntimeException("Reading of audio buffer failed: " +
                              getBufferReadFailureReason(result));
                  }
                  outStream.write(buffer.array(), 0, BUFFER_SIZE);
                  buffer.clear();
              }
          } catch (IOException e) {
              throw new RuntimeException("Writing of recorded audio failed", e);
          }
      }

      private String getBufferReadFailureReason(int errorCode) {
          switch (errorCode) {
              case AudioRecord.ERROR_INVALID_OPERATION:
                  return "ERROR_INVALID_OPERATION";
              case AudioRecord.ERROR_BAD_VALUE:
                  return "ERROR_BAD_VALUE";
              case AudioRecord.ERROR_DEAD_OBJECT:
                  return "ERROR_DEAD_OBJECT";
              case AudioRecord.ERROR:
                  return "ERROR";
              default:
                  return "Unknown (" + errorCode + ")";
          }
      }
  }
}

