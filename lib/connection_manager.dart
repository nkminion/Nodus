import 'dart:convert';
import 'package:nearby_connections/nearby_connections.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:flutter/foundation.dart';
import 'util_classes.dart';
import 'dart:async';
import 'package:uuid/uuid.dart';
import 'package:shared_preferences/shared_preferences.dart';

class ConnectionManager
{
  static final ConnectionManager instance = ConnectionManager._();
  ConnectionManager._();

  Timer? _discoveryTimer;

  final nearby = Nearby();
  final uuid = Uuid();

  String? myUID;

  ValueNotifier<List<User>> connectedNodes = ValueNotifier([]);

  final Set<String> _pendingConnections = {};

  final StreamController<Message> _messageStreamController = StreamController.broadcast();
  Stream<Message> get messageStream => _messageStreamController.stream;

  Future<void> _loadUID() async
  {
    final prefs = await SharedPreferences.getInstance();

    String? id = prefs.getString('UID');

    if (id == null)
    {
      id = const Uuid().v4();
      await prefs.setString('UID', id);
    }

    myUID = id;
  }

  Future<void> init(String userName) async
  {

    await _loadUID();

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

      _discoveryTimer?.cancel();
      _discoveryTimer = Timer.periodic(Duration(seconds: 20), (timer){
        nearby.stopDiscovery();
        _startDiscovery(userName);
      });
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
          if (status == Status.CONNECTED)
          {
            _sendHandShakeId(id,userName);
          }
          else
          {
            print('Connection Failed: $status');
          }
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
          bool connected = connectedNodes.value.any((user) => user.uid == id);
          bool pending = _pendingConnections.contains(id);
          if (connected || pending)
          {
            print('Connected or pending');
            return;
          }
          print('Found peer: $name. Connecting...');
          _pendingConnections.add(id);
          nearby.requestConnection(
            userName,
            id,
            onConnectionInitiated: _onConnectionInitiated,
            onConnectionResult: (id,status){
              print('Discovery connection result: $status');
              _pendingConnections.remove(id);
              if (status == Status.CONNECTED)
              {
                _sendHandShakeId(id, userName);
              }
              else
              {
                print('Connection Failed');
              }
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


    await nearby.acceptConnection(
      id,
      onPayLoadRecieved: (endPointId,payload) => _onPayLoadReceived(endPointId,payload),
    );
  }

  void _onDisconnected(String id)
  {
    _pendingConnections.remove(id);
    List<User> currentList = List.from(connectedNodes.value);
    currentList.removeWhere((user) => user.endPointId == id);
    connectedNodes.value = currentList;
    print('Disconnected: $id');
  }

  void sendMessage(String msg, String receiverId, String msgId, int timeStamp)
  {
    Map<String,dynamic> packet = {
      'type':'message',
      'id':msgId,
      'to':receiverId,
      'msg':msg,
      'timeStamp':timeStamp,
      'hops':0,
    };

    String jsonMsg = jsonEncode(packet);
    Uint8List bytes = Uint8List.fromList(utf8.encode(jsonMsg));

    for (User user in connectedNodes.value)
    {
      if (user.endPointId != null)
      {
        nearby.sendBytesPayload(user.endPointId!, bytes);
      }
    }
  }

  void dispose()
  {
    _discoveryTimer?.cancel();
    nearby.stopAdvertising();
    nearby.stopDiscovery();
    connectedNodes.value = [];
  }

  void _onPayLoadReceived(String endPointId,Payload payload)
  {
    if (payload.type == PayloadType.BYTES)
    {
      String jsonPacket = utf8.decode(payload.bytes!);
      Map<String,dynamic> packet = jsonDecode(jsonPacket);
      String type = packet['type'];
      print('Packet type: $type');
      try
      {
        if (type == 'handshake')
        {
          String receiverId = packet['uuid'];
          String dispName = packet['name'];

          User newNode = User(
            uid: receiverId,
            dispName: dispName,
            endPointId: endPointId
          );

          List<User> currentList = List.from(connectedNodes.value);
          currentList.removeWhere((u) => u.uid == receiverId);
          currentList.add(newNode);
          connectedNodes.value = currentList;
        }
        else if (type == 'message')
        {
          bool connected = connectedNodes.value.any((u) => u.endPointId == endPointId);
          if (connected)
          {
            String receiver = packet['to'];
            String msg = packet['msg'];
            if (receiver == myUID)
            {
              Message incomingMsg = Message(msgId: packet['id'], toUId: packet['to'], msg: msg, isMe: false, timeStamp: packet['timeStamp']);
              _messageStreamController.add(incomingMsg);
            }
            else
            {
              print('Ignoring message from $endPointId, need to propagate (I am $myUID)');
            }
          }
        }
      }
      catch (error)
      {
        print('Packet error!');
      }
    }
  }

  void _sendHandShakeId(String endPointId,String userName)
  {
    Map<String,dynamic> handshake = {
      'type':'handshake',
      'uuid':myUID,
      'name':userName,
    };

    String jsonPacket = jsonEncode(handshake);
    Uint8List bytes = utf8.encode(jsonPacket);
    nearby.sendBytesPayload(endPointId, bytes);
  }
}