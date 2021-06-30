import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter_webrtc/flutter_webrtc.dart';
import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';

import '../interface/media_stream_track.dart';
import 'utils.dart';

class MediaStreamTrackNative extends MediaStreamTrack {
  MediaStreamTrackNative(this._trackId, this._label, this._kind, this._enabled);

  factory MediaStreamTrackNative.fromMap(Map<dynamic, dynamic> map) {
    return MediaStreamTrackNative(
        map['id'], map['label'], map['kind'], map['enabled']);
  }

  final _channel = WebRTC.methodChannel();
  final String _trackId;
  final String _label;
  final String _kind;
  bool _enabled;

  bool _muted = false;
  void Function()? _onOutputChanged;

  @override
  set enabled(bool enabled) {
    _channel.invokeMethod('mediaStreamTrackSetEnable',
        <String, dynamic>{'trackId': _trackId, 'enabled': enabled});
    _enabled = enabled;

    if (kind == 'audio') {
      _muted = !enabled;
      muted ? onMute?.call() : onUnMute?.call();
    }
  }

  @override
  bool get enabled => _enabled;

  @override
  String get label => _label;

  @override
  String get kind => _kind;

  @override
  String get id => _trackId;

  @override
  bool get muted => _muted;

  @override
  Future<bool> hasTorch() => _channel.invokeMethod<bool>(
        'mediaStreamTrackHasTorch',
        <String, dynamic>{'trackId': _trackId},
      ).then((value) => value ?? false);

  @override
  Future<void> setTorch(bool torch) => _channel.invokeMethod(
        'mediaStreamTrackSetTorch',
        <String, dynamic>{'trackId': _trackId, 'torch': torch},
      );

  @override
  Future<bool> switchCamera() => Helper.switchCamera(this);

  @override
  void enableSpeakerphone(bool enable) async {
    print('MediaStreamTrack:enableSpeakerphone $enable');
    await _channel.invokeMethod(
      'enableSpeakerphone',
      <String, dynamic>{'trackId': _trackId, 'enable': enable},
    );
  }

  @override
  void setBluetoothScoOn(bool isOn) async {
    print('MediaStreamTrack:setBluetoothScoOn $isOn');
    await _channel.invokeMethod(
      'setBluetoothScoOn',
      <String, dynamic>{'trackId': _trackId,},
    );
  }

  @override
  void setReceiverOn(bool isMicrophoneOn) async {
    print('MediaStreamTrack:setReceiver $isMicrophoneOn');
    await _channel.invokeMethod(
      'setReceiverOn',
      <String, dynamic>{'trackId': _trackId,},
    );
  }

  void setSpeakerOnFromBluetooth() async{
    print('MediaStreamTrack:setSpeakerOnFromBluetooth');
    await _channel.invokeMethod(
      'setSpeakerOnFromBluetooth',
      <String, dynamic>{'trackId': _trackId,},
    );
  }

  Future<List<String>> getAudioDevices() async {
    final List<dynamic> list = await _channel.invokeMethod('getAudioDevices', <String, dynamic>{'trackId': _trackId,},);
    
    List<String> arr = [];
    list.forEach((data) {
      print("hello data $data");
      arr.add(data);
    });
    print("hello arr $arr");
    return arr;
  }

  Future<String> getCurrentOutput() async{
    final String currentOutput = await _channel.invokeMethod('getCurrentOutput', <String, dynamic>{'trackId': _trackId,},);
    return currentOutput;
  }

  void setListener(void Function() onOutputChanged) {
    print("mediastreamtrackimpl setLIstener called");
    _onOutputChanged = onOutputChanged;
    _channel.setMethodCallHandler(_methodHandle);
  }

  Future<void> _methodHandle(dynamic call) async {
    if (_onOutputChanged == null) return;
    switch (call.method) {
      case "setListener":
        print("output chanegd mediastreamtrack impl if function null or not = ${_onOutputChanged == null}");
        return _onOutputChanged!();
      default:
        break;
    }
  }

  @override

  Future<ByteBuffer> captureFrame() async {
    var filePath = await getTemporaryDirectory();
    await _channel.invokeMethod<void>(
      'captureFrame',
      <String, dynamic>{
        'trackId': _trackId,
        'path': filePath.path + '/captureFrame.png'
      },
    );
    return File(filePath.path + '/captureFrame.png')
        .readAsBytes()
        .then((value) => value.buffer);
  }
  

  @override
  Future<void> applyConstraints([Map<String, dynamic>? constraints]) {
    if (constraints == null) return Future.value();

    var _current = getConstraints();
    if (constraints.containsKey('volume') &&
        _current['volume'] != constraints['volume']) {
      setVolume(constraints['volume']);
    }

    return Future.value();
  }

  @override
  Future<void> dispose() async {
    return stop();
  }

  @override
  Future<void> stop() async {
    await _channel.invokeMethod(
      'trackDispose',
      <String, dynamic>{'trackId': _trackId},
    );
  }
}
