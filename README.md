# flutter_bluetooth

Extensions to allow scanning, connecting and communicating with Bluetooth Classic and LE devices from Flutter applications

## Getting Started
This project is largely based on the excellent https://github.com/kakzaki/blue_thermal_printer library. It's been rewritten in places to use the newer Flutter APIs and 
remove printer specific features with a view to implement them in a separate library.

Examples will be created shortly. However, in the meantime the blue_thermal_printer examples are a good starting point plus the following for read / write operations:

```
List<int> data = [
      StartTransaction,
      ...CommandStartLabel,
      Escape,
      ...ascii.encode("CS06"),
      Escape,
      ...ascii.encode("#F1A"),
      Escape,
      ...ascii.encode("V004"),
      Escape,
      ...ascii.encode("GM" + b.lengthInBytes.toString() + ","),
      ...b.buffer.asUint8List(0),

      // Text
      // Escape,
      // ...ascii.encode("X22,Hello World"),

      // QR
      // Escape,
      // ...ascii.encode("2D30,L,05,0,0"),
      // Escape,
      // ...ascii.encode("DN" + url.length.toString() + "," + url),
      Escape,
      ...ascii.encode("Q0001"),
      ...CommandEndLabel,
      EndTransaction
    ];

    FlutterBluetooth.isConnected.then((isConnected) async {
      if (isConnected != null || isConnected!) {
        FlutterBluetooth.write(Uint8List.fromList([0x05])); // ENQ
        sleep(Duration(milliseconds: 100));
        await FlutterBluetooth.read();

        FlutterBluetooth.write(Uint8List.fromList(data)); // DATA

        sleep(Duration(milliseconds: 100));
        await FlutterBluetooth.read();

        FlutterBluetooth.write(Uint8List.fromList([0x05])); // ENQ
        sleep(Duration(milliseconds: 100));
        await FlutterBluetooth.read();

        FlutterBluetooth.write(Uint8List.fromList([0x05])); // ENQ
        sleep(Duration(milliseconds: 100));
        await FlutterBluetooth.read();
      }
    });
```

# TODO
- Support for IOS / Windows
- Better documentation and examples
- Allow verbose logging to be configured