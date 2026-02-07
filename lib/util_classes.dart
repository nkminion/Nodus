class User
{
  final String uid;
  String dispName;
  int hops;
  String? endPointId;

  User({required this.uid, required this.dispName, this.hops = 1, this.endPointId});

  factory User.fromJson(Map<String,dynamic> map)
  {
    print("User database Keys: ${map.keys}");
    return User(uid: map['UID'], dispName: map['DispName']);
  }
}

class Message
{
  final String msgId;
  final String toUId;
  final String msg;
  final bool isMe;
  final int timeStamp;

  Message({required this.msgId, required this.toUId, required this.msg, required this.isMe, required this.timeStamp});

  factory Message.fromJson(Map<String,dynamic> map)
  {
    print("Msg database Keys: ${map.keys}");
    return Message(msgId: map['MsgID'], toUId: map['ToUID'], msg: map['Msg'], isMe: map['IsMe'] == 1, timeStamp: map['TimeStamp']);
  }
}