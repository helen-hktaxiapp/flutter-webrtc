package com.cloudwebrtc.webrtc;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import com.cloudwebrtc.webrtc.MethodCallHandlerImpl.AudioManager;
import com.cloudwebrtc.webrtc.utils.RTCAudioManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import io.flutter.view.TextureRegistry;
import java.util.Set;
import java.util.HashSet;

/**
 * FlutterWebRTCPlugin
 */
public class FlutterWebRTCPlugin implements FlutterPlugin, ActivityAware {

  static public final String TAG = "FlutterWebRTCPlugin";

  private RTCAudioManager rtcAudioManager;
  private static MethodChannel channel;
  private MethodCallHandlerImpl methodCallHandler;

  public FlutterWebRTCPlugin() { 
  }

  /**
   * Plugin registration.
   */
  public static void registerWith(Registrar registrar) {
    final FlutterWebRTCPlugin plugin = new FlutterWebRTCPlugin();

    plugin.startListening(registrar.context(), registrar.messenger(), registrar.textures());

    if (registrar.activeContext() instanceof Activity) {
      plugin.methodCallHandler.setActivity((Activity) registrar.activeContext());
    }

    registrar.addViewDestroyListener(view -> {
      plugin.stopListening();
      return false;
    });
  }

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
    startListening(binding.getApplicationContext(), binding.getBinaryMessenger(),
        binding.getTextureRegistry());
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    stopListening();
  }

  @Override
  public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
    methodCallHandler.setActivity(binding.getActivity());
  }

  @Override
  public void onDetachedFromActivityForConfigChanges() {
    methodCallHandler.setActivity(null);
  }

  @Override
  public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
    methodCallHandler.setActivity(binding.getActivity());
  }

  @Override
  public void onDetachedFromActivity() {
    methodCallHandler.setActivity(null);
  }

  private void startListening(final Context context, BinaryMessenger messenger,
      TextureRegistry textureRegistry) {
    methodCallHandler = new MethodCallHandlerImpl(context, messenger, textureRegistry,
        new AudioManager() {
          @Override
          public void onAudioManagerRequested(boolean requested) {
            if (requested) {
              if (rtcAudioManager == null) {
                rtcAudioManager = RTCAudioManager.create(context);
              }
              rtcAudioManager.start(FlutterWebRTCPlugin.this::onAudioManagerDevicesChanged);
            } else {
              if (rtcAudioManager != null) {
                rtcAudioManager.stop();
                rtcAudioManager = null;
              }
            }
          }

          @Override
          public void setMicrophoneMute(boolean mute) {
            if (rtcAudioManager != null) {
              rtcAudioManager.setMicrophoneMute(mute);
            }
          }

          @Override
          public void setSpeakerphoneOn(boolean on) {
            if (rtcAudioManager != null) {
              rtcAudioManager.setSpeakerphoneOn(on);
              rtcAudioManager.setBluetoothScoOn(!on);
              // listener.onChanged();
              Log.d(TAG, "listener called from setspeakeron");

            }
          }

          @Override
          public void setBluetoothScoOn(boolean on) {
            if (rtcAudioManager != null) {
              rtcAudioManager.setBluetoothScoOn(true);
              rtcAudioManager.setSpeakerphoneOn(false);
              // listener.onChanged();
              Log.d(TAG, "listener called from setbluetoothon");
            }
          }

          public void setReceiverOn(boolean on) {
            if (rtcAudioManager != null) {
              rtcAudioManager.setMicrophoneMute(false);
              rtcAudioManager.setReceiverOn(true);
              // listener.onChanged();
              Log.d(TAG, "listener called from setreceiveron");
            }
          }

          public List getAudioDevices(){
            Log.d(TAG, "Getting audio devices...");
            List<String> audioDeviceList = new ArrayList<String>();
            Set audioDeviceSet = new HashSet();
            if(rtcAudioManager != null){
              // rtcAudioManager.updateAudioDeviceState();
              audioDeviceSet = rtcAudioManager.getAudioNames();
              System.out.println("FlutterWebRTCPlugin getAudioDevices" + audioDeviceSet);
              for(Object object : audioDeviceSet){
                audioDeviceList.add(String.valueOf(object));
              }
            }
            System.out.println("FlutterWebRTCPlugin getAudioDevicesList" + audioDeviceList);

            return audioDeviceList;
          }

          public void setSpeakerOnFromBluetooth() {
            Log.d(TAG, "setspeakeronfrombluetooth1");
            
            if (rtcAudioManager != null) {
              rtcAudioManager.setSpeakerOnFromBluetooth();
              // rtcAudioManager.setSpeakerphoneOn(true);
              // rtcAudioManager.setBluetoothScoOn(false);
              // listener.onChanged();
              Log.d(TAG, "setspeakeronfrombluetooth2");

            }
          }

          public String getCurrentOutput(){
            Log.d(TAG, "Getting current output...");
            String currentOutput = "";
            if(rtcAudioManager != null){
              currentOutput = rtcAudioManager.getCurrentOutput();
            }
            Log.d(TAG, "Current output = " + currentOutput);
            return currentOutput;
          }

          public void setListener(){
            Log.d(TAG, "setlistener called from flutterwebrtcplugin java");
            rtcAudioManager.updateAudioDeviceState();
          }
        });

    channel = new MethodChannel(messenger, "FlutterWebRTC.Method");
    channel.setMethodCallHandler(methodCallHandler);
  }

  private void stopListening() {
    methodCallHandler.dispose();
    methodCallHandler = null;
    channel.setMethodCallHandler(null);

    if (rtcAudioManager != null) {
      Log.d(TAG, "Stopping the audio manager...");
      rtcAudioManager.stop();
      rtcAudioManager = null;
    }
  }

  public static AudioEventListener listener = new AudioEventListener() {
    @Override
    public void onChanged() {
      Log.d(TAG, "Listener on changed invoke output changed method...");
      channel.invokeMethod("setListener", 1);
    }
  };

  // This method is called when the audio manager reports audio device change,
  // e.g. from wired headset to speakerphone.
  private void onAudioManagerDevicesChanged(
      final RTCAudioManager.AudioDevice device,
      final Set<RTCAudioManager.AudioDevice> availableDevices) {
    // listener.onChanged();
    Log.d(TAG, "onAudioManagerDevicesChanged: " + availableDevices + ", "
        + "selected: " + device);
    // TODO(henrika): add callback handler.
  }

}
