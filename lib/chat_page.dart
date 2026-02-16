import 'dart:async';
import 'package:flutter/material.dart';
import 'package:nodus/connection_manager.dart';
import 'util_classes.dart';
import 'package:uuid/uuid.dart';
import 'database_helper.dart';

final uuid = Uuid();

class ChatPage extends StatefulWidget
{
	const ChatPage({super.key, required this.user, required this.myUID});
	final User user;
  final String myUID;

	@override
	State<ChatPage> createState() => _ChatPageState();
}

class _ChatPageState extends State<ChatPage>
{

  final TextEditingController messageController = TextEditingController();

  final List<Message> _messages = [];
  StreamSubscription? _msgSubscription;

  @override
  void initState()
  {
    super.initState();

    () async {
      List<Message> fetchedHist = await DatabaseHelper.instance.fetchMessages(widget.myUID,widget.user.uid);
      if (mounted)
      {
        print('Message history length: ${fetchedHist.length}');
        setState(() {
          _messages.addAll(fetchedHist);
        });
      }
    }();

    _msgSubscription = ConnectionManager.instance.messageStream.listen((msg){
      print('Received message: toUID: ${msg.toUId} | myId: ${widget.user.uid}');
      if ((msg.toUId == widget.myUID)&&(msg.fromUId == widget.user.uid))
      {
        print('Id matched');
        if (mounted)
        {
          print('Refreshing...');
          setState(() {
            _messages.add(msg);
          });
          print('Added...');
        }
      }
    });
  }

	Widget renderMessages(List<Message> messages)
	{
		return ListView.builder(
			reverse: true,
			itemCount: messages.length,
			itemBuilder: (context,index)
			{
				final message = messages[messages.length-1-index];
				return Align(
					alignment: (message.fromUId == widget.myUID) ? Alignment.centerRight : Alignment.centerLeft,
					child:
					Container(
						margin: EdgeInsets.all(10),
						padding: EdgeInsets.all(15),
						decoration: BoxDecoration(
							color: (message.fromUId == widget.myUID) ? Colors.blue : Color.fromARGB(255, 0, 0, 0),
							borderRadius: BorderRadius.circular(25),
						),
						child: Column(
              crossAxisAlignment: (message.fromUId == widget.myUID) ? CrossAxisAlignment.end : CrossAxisAlignment.start,
              children: [
                Text(message.msg),
                Text(DateTime.fromMillisecondsSinceEpoch(message.timeStamp).toString(),
                  style: TextStyle(
                    fontSize: 10,
                  ),
                )
              ],
            ),
					),
				);
			}
		);
	}

	@override
	Widget build(BuildContext context)
	{
		return Scaffold(
			appBar: 
				AppBar(
					title: ValueListenableBuilder<Map<String,User>>(
            valueListenable: ConnectionManager.instance.connectedNodes,
            builder: (context,nodesMap,child) {
              User? liveUser = nodesMap[widget.user.uid];
              Color statusColor = Colors.red;
              if (liveUser != null)
              {
                statusColor = liveUser.hops < 2 ? Colors.green : liveUser.hops < 4 ? Colors.amber : Colors.red;
              }
              return Row(
                children: [
                  Icon(
                    Icons.circle,
                    size: 10,
                    color: statusColor,
                  ),
                  Flexible(
                    fit: FlexFit.tight,
                    child: Column(
                      children: [
                        Text(widget.user.dispName),
                        Text(
                          widget.user.uid,
                          style: TextStyle(
                            fontSize: 9,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              );
            }
          )
				),
			body: 
				Column(
					children: [
						Expanded(
							child: renderMessages(_messages),
						),
						Container(
							padding: EdgeInsets.only(left:10,right:10,top: 24,bottom: 50),
							child: Row(
								children: [
									Expanded(
										child: TextField(
                      controller: messageController,
											decoration: InputDecoration(
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(50),
                        ),
                        focusedBorder: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(50),
                          borderSide: BorderSide(color: Colors.deepPurpleAccent, width: 2.0),
                        ),
                        hintText: 'Message',
                      ),
										)
									),
									Padding(
                    padding: EdgeInsetsGeometry.only(left:10,right:10),
                    child: ElevatedButton(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Colors.deepPurpleAccent,
                      ),
                      child: Icon(
                        Icons.send,
                        size: 20,
                        color: const Color.fromARGB(255, 214, 237, 255),
                      ),
                      onPressed: () {
                        final text = messageController.text;
                        if (text.isNotEmpty)
                        {
                          DatabaseHelper.instance.insertContact(widget.user.uid, widget.user.dispName);
                          String msgId = uuid.v7();
                          int timeStamp = DateTime.now().millisecondsSinceEpoch;
                          ConnectionManager.instance.sendMessage(text,widget.myUID,widget.user.uid,msgId,timeStamp);

                          Message newMsg = Message(
                                msgId: msgId,
                                fromUId: widget.myUID,
                                toUId: widget.user.uid,
                                msg: text,
                                timeStamp: timeStamp,
                              );
                          DatabaseHelper.instance.insertMessage(newMsg);
                          setState(() {
                            _messages.add(
                              newMsg
                            );
                          });
                        }
                        messageController.clear();
                      },
                    )
                  )
								],
							),
						),
					],
				)
		);
	}

  @override
  void dispose()
  {
    _msgSubscription?.cancel();
    messageController.dispose();
    super.dispose();
  }
}