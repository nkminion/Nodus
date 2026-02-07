import 'dart:async';

import 'package:flutter/material.dart';
import 'package:nodus/connection_manager.dart';
import 'util_classes.dart';
import 'package:uuid/uuid.dart';

final uuid = Uuid();

class ChatPage extends StatefulWidget
{
	const ChatPage({super.key, required this.user});
	final User user;

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

    _msgSubscription = ConnectionManager.instance.messageStream.listen((msg){
      if (msg.toUId == widget.user.uid)
      {
        if (mounted)
        {
          setState(() {
            _messages.add(msg);
          });
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
					alignment: message.isMe ? Alignment.centerRight : Alignment.centerLeft,
					child:
					Container(
						margin: EdgeInsets.all(10),
						padding: EdgeInsets.all(15),
						decoration: BoxDecoration(
							color: message.isMe ? Colors.blue : Color.fromARGB(255, 0, 0, 0),
							borderRadius: BorderRadius.circular(25),
						),
						child: Column(
              crossAxisAlignment: message.isMe ? CrossAxisAlignment.end : CrossAxisAlignment.start,
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
					title: Row(
						children: [
							Icon(
								Icons.circle,
								size: 10,
								color: widget.user.hops < 2 ? Colors.green : widget.user.hops < 4 ? Colors.amber : Colors.red,
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
					),
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
                          String msgId = uuid.v7();
                          int timeStamp = DateTime.now().millisecondsSinceEpoch;
                          ConnectionManager.instance.sendMessage(text,widget.user.uid,msgId,timeStamp);

                          setState(() {
                            _messages.add(
                              Message(
                                msgId: msgId,
                                toUId: widget.user.uid,
                                msg: text,
                                isMe: true,
                                timeStamp: timeStamp,
                              )
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