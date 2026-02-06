import 'dart:convert';
import 'package:nearby_connections/nearby_connections.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:flutter/foundation.dart';
import 'util_classes.dart';

class ConnectionManager
{
  static final ConnectionManager instance = ConnectionManager._();
  ConnectionManager._();

  final nearby = Nearby();

  ValueNotifier<List<User>> connectedNodes = ValueNotifier([]);

  Future<void> init(String userName) async
  {
    Map<Permission, PermissionStatus> statuses = await [
      Permission.location,
      Permission.bluetooth,
      Permission.bluetoothAdvertise,
      Permission.bluetoothConnect,
      Permission.bluetoothScan,
      Permission.nearbyWifiDevices,
    ].request();

    if (statuses[Permission.location]!.isGranted && statuses[Permission.bluetoothAdvertise]!.isGranted && statuses[Permission.bluetoothConnect]!.isGranted && statuses[Permission.nearbyWifiDevices]!.isGranted)
    {
      _startAdvertising(userName);
      _startDiscovery(userName);
    }
    else
    {
      print("Permissions denied!");
    }
  }

  Future<void> _startAdvertising(String userName) async
  {
    try
    {
      await nearby.startAdvertising(
        userName,
        Strategy.P2P_CLUSTER,
        onConnectionInitiated: _onConnectionInitiated,
        onConnectionResult: (id,status) {
          print('Advertising connection result: $status');
        },
        onDisconnected: _onDisconnected,
      );
    }
    catch (error)
    {
      print('Advertise Error: $error');
    }
  }

  Future<void> _startDiscovery(String userName) async
  {
    try
    {
      await nearby.startDiscovery(
        userName,
        Strategy.P2P_CLUSTER,
        onEndpointFound: (id,name,serviceId){
          print('Found peer: $name. Connecting...');
          nearby.requestConnection(
            userName,
            id,
            onConnectionInitiated: _onConnectionInitiated,
            onConnectionResult: (id,status){
              print('Discovery connection result: $status');
            },
            onDisconnected: _onDisconnected,
          );
        },
        onEndpointLost: (id) {
          print('Lost sight of $id');
        }
      );
    }
    catch (error)
    {
      print('Discover Error: $error');
    }
  }

  void _onConnectionInitiated(String id, ConnectionInfo info) async
  {
    print('Incoming connection from ${info.endpointName}');

    User newNode = User(uid: id, dispName: info.endpointName);
    List<User> currentList = List.from(connectedNodes.value);
    currentList.add(newNode);
    connectedNodes.value = currentList;

    await nearby.acceptConnection(
      id,
      onPayLoadRecieved: (endPointId,payload)
      {
        if (payload.type == PayloadType.BYTES)
        {
          String msg = String.fromCharCodes(payload.bytes!);
          print('Received from $endPointId: $msg');
        }
      }
    );
  }

  void _onDisconnected(String id)
  {
    List<User> currentList = List.from(connectedNodes.value);
    currentList.removeWhere((user) => user.uid == id);
    connectedNodes.value = currentList;
    print('Disconnected: $id');
  }

  void sendMessage(String msg)
  {
    Uint8List bytes = Uint8List.fromList(utf8.encode(msg));

    for (User user in connectedNodes.value)
    {
      nearby.sendBytesPayload(user.uid, bytes);
    }
  }

}