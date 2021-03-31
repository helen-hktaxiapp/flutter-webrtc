import 'dart:async';

import 'package:flutter_webrtc/flutter_webrtc.dart';

import '../interface/media_stream_track.dart';
import 'utils.dart';

class MediaStreamTrackNative extends MediaStreamTrack {
  MediaStreamTrackNative(this._trackId, this._label, this._kind, this._enabled, this._onBluetooth);
  factory MediaStreamTrackNative.fromMap(Map<dynamic, dynamic> map) {
    return MediaStreamTrackNative(
        map['id'], map['label'], map['kind'], map['enabled'], map['on']);
  }
  final _channel = WebRTC.methodChannel();
  final String _trackId;
  final String _label;
  final String _kind;
  bool _enabled;

  bool _muted = false;
  //helen
  bool _onBluetooth = false;

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

  //helen
  // @override
  // set onBluetooth(bool onBluetooth){
  //   _channel.invokeMethod('mediaStreamTrackSetEnable',
  //       <String, dynamic>{'trackId': _trackId, 'on': _onBluetooth});
  //   _onBluetooth = onBluetooth;

  //   if (kind == 'audio') {
  //     _muted = !onBluetooth;
  //     muted ? onMute?.call() : onUnMute?.call();
  //   }
  // }
  @override
  bool get onBluetooth => _onBluetooth;

  @override
  Future<bool> hasTorch() => _channel.invokeMethod(
        'mediaStreamTrackHasTorch',
        <String, dynamic>{'trackId': _trackId},
      );

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

  //helen
  @override
  void setBluetoothScoOn(bool onBluetooth) async {
    print('MediaStreamTrack:setBluetoothScoOn $onBluetooth');
    await _channel.invokeMethod(
      'setBluetoothScoOn',
      <String, dynamic>{'trackId': _trackId, 'on': onBluetooth},
    );
  }

  @override
  Future<dynamic> captureFrame([String filePath]) {
    return _channel.invokeMethod<void>(
      'captureFrame',
      <String, dynamic>{'trackId': _trackId, 'path': filePath},
    );
  }

  @override
  Future<void> applyConstraints([Map<String, dynamic> constraints]) {
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
