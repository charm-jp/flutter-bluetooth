package jp.charm.flutter_bluetooth;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

import io.flutter.Log;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.EventChannel.StreamHandler;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;

import io.flutter.plugin.common.EventChannel.EventSink;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;

/** FlutterBluetoothPlugin */
public class FlutterBluetoothPlugin implements FlutterPlugin, MethodCallHandler, StreamHandler, ActivityAware, PluginRegistry.RequestPermissionsResultListener {
  private static final String ACTION_DEVICE_LIST_UPDATED = "jp.charm.flutter_bluetooth.DEVICE_LIST_UPDATED";

  // Dart-side event definitions
  private static final int STATE_OFF = 10;
  private static final int STATE_TURNING_ON = 11;
  private static final int STATE_ON = 12;
  private static final int STATE_TURNING_OFF = 13;
  private static final int STATE_BLE_TURNING_ON = 14;
  private static final int STATE_BLE_ON = 15;
  private static final int STATE_BLE_TURNING_OFF = 16;
  private static final int ERROR = -1;
  private static final int CONNECTED = 1;
  private static final int DISCONNECTED = 0;
  private static final int DISCOVERY_STARTED = 2;
  private static final int DISCOVERY_FINISHED = 3;
  private static final int DEVICE_LIST_UPDATED = 20;
  private static final UUID MY_UUID = UUID.fromString("7676b80a-5b43-486c-82bf-e3bc266b2bf0");
  private static final int REQUEST_COARSE_LOCATION_PERMISSIONS = 87657;
  private static final String TAG = "flutter_bluetooth";

  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private Context applicationContext;
  private Activity applicationActivity;
  private BroadcastReceiver bluetoothStatusReceiver;

  private MethodChannel methodChannel;
  private EventChannel eventChannel;
  private BluetoothAdapter mBluetoothAdapter;
  private BluetoothAdapter.LeScanCallback leScanCallback = null;
  private static ConnectedThread THREAD = null;
  private Result pendingResult = null;
  private  MethodCall pendingMethodCall = null;

  private TreeSet<Device> foundDevices = new TreeSet<Device>(new DeviceCompare());

  private static class MethodResultWrapper implements Result {
    private Result methodResult;
    private Handler handler;

    MethodResultWrapper(Result result) {
      methodResult = result;
      handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void success(final Object result) {
      handler.post(new Runnable() {
        @Override
        public void run() {
          methodResult.success(result);
        }
      });
    }

    @Override
    public void error(final String errorCode, final String errorMessage, final Object errorDetails) {
      handler.post(new Runnable() {
        @Override
        public void run() {
          methodResult.error(errorCode, errorMessage, errorDetails);
        }
      });
    }

    @Override
    public void notImplemented() {
      handler.post(new Runnable() {
        @Override
        public void run() {
          methodResult.notImplemented();
        }
      });
    }
  }

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    this.applicationContext = flutterPluginBinding.getApplicationContext();

    BluetoothManager mBluetoothManager = (BluetoothManager) applicationContext.getSystemService(Context.BLUETOOTH_SERVICE);
    mBluetoothAdapter = mBluetoothManager.getAdapter();

    methodChannel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "flutter_bluetooth/method");
    methodChannel.setMethodCallHandler(this);

    eventChannel = new EventChannel(flutterPluginBinding.getBinaryMessenger(), "flutter_bluetooth/event");
    eventChannel.setStreamHandler(this);
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result rawResult) {
    Result result = new MethodResultWrapper(rawResult);

    if (mBluetoothAdapter == null && !"isAvailable".equals(call.method)) {
      result.error("bluetooth_unavailable", "the device does not have bluetooth", null);
      return;
    }

    final Map<String, Object> arguments = call.arguments();

    switch (call.method) {
      case "getPlatformVersion":
        result.success("Android " + android.os.Build.VERSION.RELEASE);
        break;
      case "isAvailable":
        result.success(mBluetoothAdapter != null);
        break;
      case "isOn":
        try {
          assert mBluetoothAdapter != null;
          result.success(mBluetoothAdapter.isEnabled());
        } catch (Exception ex) {
          result.error("Error", ex.getMessage(), ex);
        }
        break;
      case "isConnected":
        result.success(THREAD != null);
        break;

      case "openSettings":
        ContextCompat.startActivity(applicationContext, new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS),
                null);
        result.success(true);
        break;
      case "connect":
        if (arguments.containsKey("address") && arguments.containsKey("service")) {
          String address = (String) arguments.get("address");
          UUID service = UUID.fromString((String) arguments.get("service"));
          connect(result, address, service);
        } else {
          result.error("invalid_argument", "argument 'address' not found", null);
        }
        break;
      case "disconnect":
        disconnect(result);
        break;
      case "discover":
        try {
          if (hasPermissions(call, result)) {
            discover(result);
          }
        } catch (Exception ex) {
          result.error("Error", ex.getMessage(), ex);
        }
        break;
      case "bonded":
        try {
          if (hasPermissions(call, result)) {
            bonded(result);
          }
        } catch (Exception ex) {
          result.error("Error", ex.getMessage(), ex);
        }
        break;
      case "getDeviceList":
        List<Map<String, Object>> list = new ArrayList<>();

        for (Device device : foundDevices) {
          Map<String, Object> ret = new HashMap<>();
          ret.put("address", device.address);
          ret.put("name", device.name);
          ret.put("type", device.type);
          ret.put("bonded", device.bonded);
          ret.put("connected", device.connected);
          list.add(ret);
        }

        result.success(list);
        break;
      case "read":
        read(result);
        break;
      case "write":
        if (arguments.containsKey("message")) {
          byte[] message = (byte[]) arguments.get("message");
          write(result, message);
        } else {
          result.error("invalid_argument", "argument 'message' not found", null);
        }
        break;
      default:
        result.notImplemented();
    }
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    methodChannel.setMethodCallHandler(null);
  }

  @Override
  public void onListen(Object arguments, EventSink events) {
    bluetoothStatusReceiver = createBluetoothStateReceiver(events);

    applicationContext.registerReceiver(bluetoothStatusReceiver, new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED));
    applicationContext.registerReceiver(bluetoothStatusReceiver, new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED));
    applicationContext.registerReceiver(bluetoothStatusReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
    applicationContext.registerReceiver(bluetoothStatusReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED));
    applicationContext.registerReceiver(bluetoothStatusReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));

    applicationContext.registerReceiver(bluetoothStatusReceiver, new IntentFilter(ACTION_DEVICE_LIST_UPDATED));
  }

  @Override
  public void onCancel(Object arguments) {
    applicationContext.unregisterReceiver(bluetoothStatusReceiver);
  }

  private BroadcastReceiver createBluetoothStateReceiver(final EventSink events) {
    return new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        switch (action) {
          case BluetoothDevice.ACTION_FOUND:
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            foundDevices.add(new Device(
                    device.getName(),
                    device.getAddress(),
                    device.getType(),
                    device.getBondState() == BluetoothDevice.BOND_BONDED,
                    false
                    ));

            context.sendBroadcast(new Intent(ACTION_DEVICE_LIST_UPDATED));
            break;
          case ACTION_DEVICE_LIST_UPDATED:
            events.success(DEVICE_LIST_UPDATED);
            break;
          case BluetoothAdapter.ACTION_DISCOVERY_STARTED:
            events.success(DISCOVERY_STARTED);
            break;
          case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
            events.success(DISCOVERY_FINISHED);
            break;
          case BluetoothAdapter.ACTION_STATE_CHANGED:
            events.success(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1));
            break;
          case BluetoothDevice.ACTION_ACL_CONNECTED:
            events.success(CONNECTED);
            break;
          case BluetoothDevice.ACTION_ACL_DISCONNECTED:
            THREAD = null;
            events.success(DISCONNECTED);
          case BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED:
            switch (intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, -1)) {
              case BluetoothAdapter.STATE_CONNECTED:
                events.success(CONNECTED);
                break;
              case BluetoothAdapter.STATE_DISCONNECTED:
                THREAD = null;
                events.success(DISCONNECTED);
                break;
            }
            break;
        }
      }
    };
  }

  // Functions
  private void connect(Result result, String address, UUID service) {
    if (THREAD != null) {
      result.error("connect_error", "already connected", null);
      return;
    }
    AsyncTask.execute(() -> {
      try {
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);

        if (device == null) {
          result.error("connect_error", "device not found", null);
          return;
        }

        BluetoothSocket socket = device.createRfcommSocketToServiceRecord(service);

        if (socket == null) {
          result.error("connect_error", "socket connection not established", null);
          return;
        }

        // Cancel bt discovery, even though we didn't start it
        mBluetoothAdapter.cancelDiscovery();

        try {
          socket.connect();
          THREAD = new ConnectedThread(socket);
          THREAD.start();

          // Set the connection status
          for (Device dev : foundDevices) {
            if (dev.address.equals(address)) {
              dev.connected = true;
              applicationContext.sendBroadcast(new Intent(ACTION_DEVICE_LIST_UPDATED));
            }
          }

          result.success(true);
        } catch (Exception ex) {
          Log.e(TAG, ex.getMessage(), ex);
          result.error("connect_error", ex.getMessage(), ex);
        }
      } catch (Exception ex) {
        Log.e(TAG, ex.getMessage(), ex);
        result.error("connect_error", ex.getMessage(), ex);
      }
    });
  }

  private void disconnect(Result result) {
    if (THREAD == null) {
      result.error("disconnection_error", "not connected", null);
      return;
    }
    AsyncTask.execute(() -> {
      try {
        THREAD.cancel();
        THREAD = null;
        result.success(true);
      } catch (Exception ex) {
        Log.e(TAG, ex.getMessage(), ex);
        result.error("disconnection_error", ex.getMessage(), ex);
      }
    });
  }

  private boolean hasPermissions(MethodCall call, Result result) {
    if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
      Log.d(TAG, "Need to get permissions");

      applicationActivity.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_COARSE_LOCATION_PERMISSIONS);
      pendingResult = result;
      pendingMethodCall = call;
      return false;
    } else {
      return true;
    }
  }

  private void discover(Result result) {
    try {
      // Clear the existing device list and stop any existing scan jobs
      foundDevices.clear();
      mBluetoothAdapter.cancelDiscovery();

      // TODO: Future feature
      //    if (leScanCallback != null) {
      //      mBluetoothAdapter.stopLeScan(leScanCallback);
      //    }

      // Add already bonded devices
      for (BluetoothDevice device : mBluetoothAdapter.getBondedDevices()) {
        foundDevices.add(new Device(
                device.getName(),
                device.getAddress(),
                device.getType(),
                device.getBondState() == BluetoothDevice.BOND_BONDED,
                false
                ));
      }
      applicationContext.sendBroadcast(new Intent(ACTION_DEVICE_LIST_UPDATED));

      // Start scan job
      mBluetoothAdapter.startDiscovery();
      // mBluetoothAdapter.startLeScan(leScanCallback);

      result.success(true);
    } catch (Exception ex) {
      Log.e(TAG, ex.getMessage(), ex);
      result.error("disconnection_error", ex.getMessage(), ex);
    }
  }

  private void bonded(Result result) {
    try {
      // Clear the existing device list
      foundDevices.clear();

      // Add already bonded devices
      for (BluetoothDevice device : mBluetoothAdapter.getBondedDevices()) {
        foundDevices.add(new Device(
                device.getName(),
                device.getAddress(),
                device.getType(),
                device.getBondState() == BluetoothDevice.BOND_BONDED,
                false
        ));
      }
      applicationContext.sendBroadcast(new Intent(ACTION_DEVICE_LIST_UPDATED));

      result.success(true);
    } catch (Exception ex) {
      Log.e(TAG, ex.getMessage(), ex);
      result.error("disconnection_error", ex.getMessage(), ex);
    }
  }

  private void write(Result result, byte[] message) {
    if (THREAD == null) {
      result.error("write_error", "not connected", null);
      return;
    }

    try {
      THREAD.write(message);
      result.success(true);
    } catch (Exception ex) {
      Log.e(TAG, ex.getMessage(), ex);
      result.error("write_error", ex.getMessage(), ex);
    }
  }

  private void read(Result result) {
    if (THREAD == null) {
      result.error("write_error", "not connected", null);
      return;
    }
    try {
      result.success(THREAD.read());
    } catch (Exception ex) {
      Log.e(TAG, ex.getMessage(), ex);
      result.error("write_error", ex.getMessage(), ex);
    }
  }

  @Override
  public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
    applicationActivity = binding.getActivity();
    binding.addRequestPermissionsResultListener(this);
  }

  @Override
  public void onDetachedFromActivityForConfigChanges() {
    applicationActivity = null;
  }

  @Override
  public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
    applicationActivity = binding.getActivity();
    binding.addRequestPermissionsResultListener(this);
  }

  @Override
  public void onDetachedFromActivity() {
    applicationActivity = null;
  }

  @Override
  public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    if (requestCode == REQUEST_COARSE_LOCATION_PERMISSIONS) {
      if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        onMethodCall(pendingMethodCall, pendingResult);
      } else {
        pendingResult.error("no_permissions", "this plugin requires location permissions for scanning", null);
        pendingResult = null;
      }
      return true;
    }
    return false;
  }

  // Classes
  private class Device {
    final public String name;
    final public String address;
    final int type;
    boolean bonded;
    boolean connected;

    Device(String name, String address, int type, boolean bonded, boolean connected) {
      this.name = name;
      this.address = address;
      this.type = type;
      this.bonded = bonded;
      this.connected = connected;
    }
  }

  class DeviceCompare implements Comparator<Device>{
    @Override
    public int compare(Device o1, Device o2) {
      return o1.address.compareTo(o2.address);
    }
  }

  private class ConnectedThread extends Thread {
    private final BluetoothSocket mmSocket;
    private final InputStream inputStream;
    private final OutputStream outputStream;

    ConnectedThread(BluetoothSocket socket) {
      mmSocket = socket;
      InputStream tmpIn = null;
      OutputStream tmpOut = null;

      try {
        tmpIn = socket.getInputStream();
        tmpOut = socket.getOutputStream();
      } catch (IOException e) {
        e.printStackTrace();
      }
      inputStream = tmpIn;
      outputStream = tmpOut;
    }

    public void write(byte[] bytes) {
      try {
        outputStream.write(bytes);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    public byte[] read() {
      try {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        boolean avail = true;
        while (avail && inputStream.available() > 0) {
          nRead = inputStream.read();

          if (nRead == 0x03 || nRead == -1) {
            avail = false;
          }

          buffer.write(nRead);
        }

        buffer.flush();

        return buffer.toByteArray();
      } catch (IOException e) {
        e.printStackTrace();
      }

      return null;
    }

    public void cancel() {
      try {
        outputStream.flush();
        outputStream.close();

        inputStream.close();

        mmSocket.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  public static String byteArrayToHex(byte[] a) {
    StringBuilder sb = new StringBuilder();
    for(byte b: a) {
      switch (b) {
        case 0x02:
          sb.append("<STX>");
          break;
        case 0x03:
          sb.append("<ETX>");
          break;
        case 0x1B:
          sb.append("<ESCAPE>");
          break;
        case 0x05:
          sb.append("<ENQ>");
          break;
        case 0x06:
          sb.append("<ACQ>");
          break;
        default:
          if (b > 47 && b < 123) {
            sb.append(String.format("%c", b));
          } else {
            sb.append(String.format("%02X", b));
          }
      }
    }

    return sb.toString();
  }
}
