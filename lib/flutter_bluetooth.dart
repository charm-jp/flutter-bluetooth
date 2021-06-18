
import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/services.dart';

class FlutterBluetooth {
  static const ACTION_DEVICE_LIST_UPDATED = "jp.charm.flutter_bluetooth.DEVICE_LIST_UPDATED";

  static const MethodChannel _methodChannel = MethodChannel('flutter_bluetooth/method');
  static const EventChannel _eventChannel = EventChannel('flutter_bluetooth/event');

  static Future<String?> get platformVersion async {
    final String? version = await _methodChannel.invokeMethod('getPlatformVersion');
    return version;
  }

  static Future<bool> get isAvailable async {
    return await _methodChannel.invokeMethod('isAvailable') ?? false;
  }

  static Future<bool> get isOn async {
    return await _methodChannel.invokeMethod('isOn') ?? false;
  }

  static Future<bool> get isConnected async {
    return await _methodChannel.invokeMethod('isConnected') ?? false;
  }

  static Future<void> discover() async {
    return await _methodChannel.invokeMethod('discover');
  }

  static Future<void> bonded() async {
    return await _methodChannel.invokeMethod('bonded');
  }

  static Future<dynamic> connect(String address, String serviceUUID) async {
    return await _methodChannel.invokeMethod('connect', {'address': address, 'service': serviceUUID});
  }

  static Future<dynamic> disconnect() async {
    return await _methodChannel.invokeMethod('disconnect');
  }

  static Future<Uint8List> read() async {
    return await _methodChannel.invokeMethod('read');
  }

  static Future<dynamic> write(Uint8List message) async {
    return await _methodChannel.invokeMethod('write', {'message': message});
  }

  static Future<List<BluetoothDevice>> get getDeviceList async {
    final List list = await (_methodChannel.invokeMethod('getDeviceList'));
    return list.map((map) => BluetoothDevice.fromMap(map)).toList();
  }

  static Stream<int> get onBluetoothEvent {
    return _eventChannel.receiveBroadcastStream().map((event) => event);
  }
}

class BluetoothDevice {
  final String name;
  final String address;
  final int type;
  final bool bonded;
  final bool connected;

  BluetoothDevice(this.name, this.address, this.type, this.bonded, this.connected);

  factory BluetoothDevice.fromMap(Map map) {
    return BluetoothDevice(
      map['name'] ?? 'Unknown',
      map['address'],
      map['type'],
      map['bonded'],
      map['connected'],
    );
  }

  @override
  operator ==(Object other) {
    return other is BluetoothDevice && other.address == address;
  }

  @override
  int get hashCode => address.hashCode;
}

class DeviceType {
  static const UNKNOWN = 0;
  static const CLASSIC = 1;
  static const LE = 2;
  static const DUAL = 3;
}

class BTEvent {
  static const int STATE_OFF = 10;
  static const int STATE_TURNING_ON = 11;
  static const int STATE_ON = 12;
  static const int STATE_TURNING_OFF = 13;
  static const int STATE_BLE_TURNING_ON = 14;
  static const int STATE_BLE_ON = 15;
  static const int STATE_BLE_TURNING_OFF = 16;
  static const int ERROR = -1;
  static const int CONNECTED = 1;
  static const int DISCONNECTED = 0;
  static const int DISCOVERY_STARTED = 2;
  static const int DISCOVERY_FINISHED = 3;
  static const int DEVICE_LIST_UPDATED = 20;
}

class BTService {
  static const String ServiceDiscoveryServerServiceClassID= '00001000-0000-1000-8000-00805F9B34FB';
  static const String BrowseGroupDescriptorServiceClassID = '00001001-0000-1000-8000-00805F9B34FB';
  static const String PublicBrowseGroupServiceClass = '00001002-0000-1000-8000-00805F9B34FB';
  static const String SerialPortServiceClass = '00001101-0000-1000-8000-00805F9B34FB';
  static const String LANAccessUsingPPPServiceClass = '00001102-0000-1000-8000-00805F9B34FB';
  static const String DialupNetworkingServiceClas = '00001103-0000-1000-8000-00805F9B34FB';
  static const String IrMCSyncServiceClass = '00001104-0000-1000-8000-00805F9B34FB';
  static const String OBEXObjectPushServiceClass= '00001105-0000-1000-8000-00805F9B34FB';
  static const String OBEXFileTransferServiceClass = '00001106-0000-1000-8000-00805F9B34FB';
  static const String IrMCSyncCommandServiceClass= '00001107-0000-1000-8000-00805F9B34FB';
  static const String HeadsetServiceClass = '00001108-0000-1000-8000-00805F9B34FB';
  static const String CordlessTelephonyServiceClass = '00001109-0000-1000-8000-00805F9B34FB';
  static const String AudioSourceServiceClass = '0000110A-0000-1000-8000-00805F9B34FB';
  static const String AudioSinkServiceClass= '0000110B-0000-1000-8000-00805F9B34FB';
  static const String AVRemoteControlTargetServiceClass = '0000110C-0000-1000-8000-00805F9B34FB';
  static const String AdvancedAudioDistributionServiceClass = '0000110D-0000-1000-8000-00805F9B34FB';
  static const String AVRemoteControlServiceClass= '0000110E-0000-1000-8000-00805F9B34FB';
  static const String VideoConferencingServiceClass = '0000110F-0000-1000-8000-00805F9B34FB';
  static const String IntercomServiceClass = '00001110-0000-1000-8000-00805F9B34FB';
  static const String FaxServiceClass = '00001111-0000-1000-8000-00805F9B34FB';
  static const String HeadsetAudioGatewayServiceClass= '00001112-0000-1000-8000-00805F9B34FB';
  static const String WAPServiceClass = '00001113-0000-1000-8000-00805F9B34FB';
  static const String WAPClientServiceClass = '00001114-0000-1000-8000-00805F9B34FB';
  static const String PANUServiceClass = '00001115-0000-1000-8000-00805F9B34FB';
  static const String NAPServiceClass = '00001116-0000-1000-8000-00805F9B34FB';
  static const String GNServiceClass = '00001117-0000-1000-8000-00805F9B34FB';
  static const String DirectPrintingServiceClass = '00001118-0000-1000-8000-00805F9B34FB';
  static const String ReferencePrintingServiceClass = '00001119-0000-1000-8000-00805F9B34FB';
  static const String ImagingServiceClass= '0000111A-0000-1000-8000-00805F9B34FB';
  static const String ImagingResponderServiceClass = '0000111B-0000-1000-8000-00805F9B34FB';
  static const String ImagingAutomaticArchiveServiceClass = '0000111C-0000-1000-8000-00805F9B34FB';
  static const String ImagingReferenceObjectsServiceClass = '0000111D-0000-1000-8000-00805F9B34FB';
  static const String HandsfreeServiceClass = '0000111E-0000-1000-8000-00805F9B34FB';
  static const String HandsfreeAudioGatewayServiceClass = '0000111F-0000-1000-8000-00805F9B34FB';
  static const String DirectPrintingReferenceObjectsServiceClass = '00001120-0000-1000-8000-00805F9B34FB';
  static const String ReflectedUIServiceClass = '00001121-0000-1000-8000-00805F9B34FB';
  static const String BasicPringingServiceClass = '00001122-0000-1000-8000-00805F9B34FB';
  static const String PrintingStatusServiceClass= '00001123-0000-1000-8000-00805F9B34FB';
  static const String HumanInterfaceDeviceServiceClass = '00001124-0000-1000-8000-00805F9B34FB';
  static const String HardcopyCableReplacementServiceClass = '00001125-0000-1000-8000-00805F9B34FB';
  static const String HCRPrintServiceClas = '00001126-0000-1000-8000-00805F9B34FB';
  static const String HCRScanServiceClass= '00001127-0000-1000-8000-00805F9B34FB';
  static const String CommonISDNAccessServiceClass = '00001128-0000-1000-8000-00805F9B34FB';
  static const String VideoConferencingGWServiceClass = '00001129-0000-1000-8000-00805F9B34FB';
  static const String UDIMTServiceClass = '0000112A-0000-1000-8000-00805F9B34FB';
  static const String UDITAServiceClass = '0000112B-0000-1000-8000-00805F9B34FB';
  static const String AudioVideoServiceClass = '0000112C-0000-1000-8000-00805F9B34FB';
  static const String SIMAccessServiceClass = '0000112D-0000-1000-8000-00805F9B34FB';
  static const String PnPInformationServiceClass= '00001200-0000-1000-8000-00805F9B34FB';
  static const String GenericNetworkingServiceClass = '00001201-0000-1000-8000-00805F9B34FB';
  static const String GenericFileTransferServiceClass = '00001202-0000-1000-8000-00805F9B34FB';
  static const String GenericAudioServiceClass= '00001203-0000-1000-8000-00805F9B34FB';
  static const String GenericTelephonyServiceClass = '00001204-0000-1000-8000-00805F9B34FB';
}