import 'dart:convert';
import 'package:nearby_connections/nearby_connections.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:flutter/foundation.dart';
import 'util_classes.dart';
import 'dart:async';
import 'package:uuid/uuid.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'database_helper.dart';

class ConnectionManager
{
  static final ConnectionManager instance = ConnectionManager._();
  ConnectionManager._();

  Timer? _discoveryTimer;
  Timer? _presenceTimer;

  final nearby = Nearby();
  final uuid = Uuid();

  String? myUID;

  ValueNotifier<Map<String,User>> connectedNodes = ValueNotifier({});
  final Set<String> _pendingConnections = {};

  final StreamController<Message> _messageStreamController = StreamController.broadcast();
  Stream<Message> get messageStream => _messageStreamController.stream;

  Set<String> idCheck = {};
  List<String> idList = [];
  int idMaxSize = 10000;

  Future<void> loadUID() async
  {
    final prefs = await SharedPreferences.getInstance();

    String? id = prefs.getString('UID');

    if (id == null)
    {
      id = uuid.v4();
      await prefs.setString('UID', id);
    }

    myUID = id;
  }

  Future<void> init(String userName) async
  {

    await loadUID();

    if (myUID != null)
    {
      await DatabaseHelper.instance.insertContact(myUID!, userName);
    }
    else
    {
      print('Skipping reg, myUID is null');
    }

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
      _discoveryTimer = Timer.periodic(Duration(seconds: 10), (timer){
        nearby.stopDiscovery();
        _startDiscovery(userName);
      });

      _presenceTimer?.cancel();
      _presenceTimer = Timer.periodic(Duration(seconds: 10), (timer){
        _broadcastPresence(1,userName);
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
        onConnectionInitiated: (id,info){
          _onConnectionInitiated(id, info, userName);
        },
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
        onDisconnected: (id){
          _onDisconnected(id,userName);
        },
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
          bool connected = connectedNodes.value.values.any((u) => u.endPointId == id);
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
            onConnectionInitiated: (id,info){
              _onConnectionInitiated(id, info, userName);
            },
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
            onDisconnected: (id){
              _onDisconnected(id,userName);
            },
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

  void _onConnectionInitiated(String id, ConnectionInfo info, String userName) async
  {
    print('Incoming connection from ${info.endpointName}');


    await nearby.acceptConnection(
      id,
      onPayLoadRecieved: (endPointId,payload) => _onPayLoadReceived(endPointId,payload,userName),
    );
  }

  void _onDisconnected(String id,String userName)
  {
    _pendingConnections.remove(id);
    Map<String,User> currentMap = Map.from(connectedNodes.value);
    currentMap.removeWhere((_,user) => user.endPointId == id);
    connectedNodes.value = currentMap;
    _broadcastPresence(5,userName);
    print('Disconnected: $id');
  }

  void sendMessage(String msg, String senderId, String receiverId, String msgId, int timeStamp)
  {
    Map<String,dynamic> packet = {
      'type':'message',
      'id':msgId,
      'from':senderId,
      'to':receiverId,
      'msg':msg,
      'timeStamp':timeStamp,
      'hops':0,
    };

    String jsonMsg = jsonEncode(packet);
    Uint8List bytes = Uint8List.fromList(utf8.encode(jsonMsg));

    for (User user in connectedNodes.value.values)
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
    connectedNodes.value = {};
  }

  void _onPayLoadReceived(String endPointId,Payload payload,String userName)
  {
    if (payload.type == PayloadType.BYTES)
    {
      String jsonPacket = utf8.decode(payload.bytes!);
      Map<String,dynamic> packet = jsonDecode(jsonPacket);
      List<Map<String,dynamic>> directory = [];
      String id = packet['id'];
      if (idCheck.contains(id))
      {
        return;
      }
      idCheck.add(id);
      idList.add(id);
      if (idList.length >= idMaxSize)
      {
        idCheck.remove(idList.first);
        idList.removeAt(0);
      }
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
            endPointId: endPointId,
            hops: 1,
          );

          Map<String,User> currentMap = Map.from(connectedNodes.value);
          currentMap.remove(receiverId);
          currentMap[receiverId] = newNode;
          connectedNodes.value = currentMap;
          _broadcastPresence(5,userName);
        }
        else if (type == 'presence')
        {
          Map<String,User> currentMap = Map.from(connectedNodes.value);

          int packetTTL = packet['ttl'];

          for (Map<String,dynamic> entry in packet['directory'])
          {
            entry['hops'] += 1;
            if (entry['uid'] == myUID)
            {
              continue;
            }
            else if (!currentMap.containsKey(entry['uid']))
            {
              User newnode = User(
                uid: entry['uid'],
                dispName: entry['name'],
                endPointId: null,
                hops: entry['hops'],
              );
              if (entry['hops'] <= packetTTL)
              {
                directory.add(
                  {
                    'uid':entry['uid'],
                    'name':entry['name'],
                    'hops':entry['hops'],
                  }
                );
              }
              currentMap[entry['uid']] = newnode;
            }
            else if (entry['hops'] < currentMap[entry['uid']]!.hops)
            {
              User oldnode = currentMap[entry['uid']]!;
              User newnode = User(
                uid: oldnode.uid,
                dispName: oldnode.dispName,
                endPointId: oldnode.endPointId,
                hops: entry['hops'],
              );
              currentMap[entry['uid']] = newnode;
              if (entry['hops'] <= packetTTL)
              {
                directory.add(
                  {
                    'uid':entry['uid'],
                    'name':entry['name'],
                    'hops':entry['hops'],
                  }
                );
              }
            }
          }
          connectedNodes.value = currentMap;
          packet['directory'] = directory;
          packet['ttl'] -= 1;
          if (packet['ttl'] > 0)
          {
            String jsonMsg = jsonEncode(packet);
            Uint8List bytes = Uint8List.fromList(utf8.encode(jsonMsg));

            for (User user in connectedNodes.value.values)
            {
              if (user.endPointId != null && user.endPointId != endPointId)
              {
                nearby.sendBytesPayload(user.endPointId!, bytes);
              }
            }
          }
        }
        else if (type == 'message')
        {
          bool connected = connectedNodes.value.values.any((u) => u.endPointId == endPointId);
          if (connected)
          {
            String receiver = packet['to'];
            String msg = packet['msg'];
            if (receiver == myUID)
            {
              String senderId = packet['from'];
              String senderName = connectedNodes.value[senderId]?.dispName ?? 'Unknown Node';
              DatabaseHelper.instance.insertContact(senderId, senderName);
              Message incomingMsg = Message(msgId: packet['id'], fromUId: packet['from'], toUId: packet['to'], msg: msg, timeStamp: packet['timeStamp']);
              DatabaseHelper.instance.insertMessage(incomingMsg);
              _messageStreamController.add(incomingMsg);
            }
            else
            {
              print('Ignoring message from $endPointId, need to propagate (I am $myUID)');
              packet['hops'] += 1;
              if (packet['hops'] < 5)
              {
                String jsonMsg = jsonEncode(packet);
                Uint8List bytes = Uint8List.fromList(utf8.encode(jsonMsg));

                for (User user in connectedNodes.value.values)
                {
                  if (user.endPointId != null && user.endPointId != endPointId)
                  {
                    nearby.sendBytesPayload(user.endPointId!, bytes);
                  }
                }
              }
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
      'id':uuid.v7(),
      'type':'handshake',
      'uuid':myUID,
      'name':userName,
    };

    String jsonPacket = jsonEncode(handshake);
    Uint8List bytes = utf8.encode(jsonPacket);
    nearby.sendBytesPayload(endPointId, bytes);
  }

  void _broadcastPresence(int timeToLive,String userName)
  {
    List<Map<String,dynamic>> directory = [];

    for (User user in connectedNodes.value.values)
    {
      directory.add(
        {
          'uid':user.uid,
          'name':user.dispName,
          'hops':user.hops,
        }
      );
    }
    directory.add(
      {
        'uid':myUID,
        'name':userName,
        'hops':0,
      }
    );

    Map<String,dynamic> packet = {
      'id':uuid.v7(),
      'type':'presence',
      'directory':directory,
      'ttl':timeToLive,
    };

    String jsonMsg = jsonEncode(packet);
    Uint8List bytes = Uint8List.fromList(utf8.encode(jsonMsg));

    for (User user in connectedNodes.value.values)
    {
      if (user.endPointId != null)
      {
        nearby.sendBytesPayload(user.endPointId!, bytes);
      }
    }
  }
}