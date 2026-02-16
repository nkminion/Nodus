class User
{
  final String uid;
  String dispName;
  int hops;
  String? endPointId;

  User({required this.uid, required this.dispName, this.hops = 5, this.endPointId});

  factory User.fromJson(Map<String,dynamic> map)
  {
    print("User database Keys: ${map.keys}");
    return User(uid: map['UID'], dispName: map['DispName']);
  }
}

class Message
{
  final String msgId;
  final String fromUId;
  final String toUId;
  final String msg;
  final int timeStamp;

  Message({required this.msgId, required this.fromUId, required this.toUId, required this.msg, required this.timeStamp});

  factory Message.fromJson(Map<String,dynamic> map)
  {
    print("Msg database Keys: ${map.keys}");
    return Message(msgId: map['MsgID'], fromUId:map['FromUID'] ,toUId: map['ToUID'], msg: map['Msg'], timeStamp: map['TimeStamp']);
  }

  Map<String,dynamic> toMap()
  {
    return {
      'MsgID':msgId,
      'FromUID':fromUId,
      'ToUID':toUId,
      'Msg':msg,
      'TimeStamp':timeStamp,
    };
  }
}