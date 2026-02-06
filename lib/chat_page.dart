import 'package:flutter/material.dart';
import 'util_classes.dart';
import 'package:uuid/uuid.dart';

final uuid = Uuid();

final List<Message> testMessages = [
	Message(msgId: '001', toUId: '002', msg: 'Mambo', isMe: true, timeStamp: 00000),
	Message(msgId: '002', toUId: '003', msg: 'WOAHHH', isMe: false, timeStamp: 00001),
	Message(msgId: '003', toUId: '004', msg: 'DUANNGGGG', isMe: true, timeStamp: 00002),
	Message(
		msgId: '004',
		toUId: '005',
		msg: "The monkey causes more problems than he's worth. His curiosity has hurt or at the very least made peoples lives more difficult just because The Man with the Yellow Hat can't be bothered to control him. After this many “lessons” the damn monkey has had after fucking everyone over, it's clear he's not gonna learn. He's gonna keep fucking up. And we're all made to pay the price just because The Man with the Yellow Hat has no sense of decency. Eventually the monkey will end up causing a death or many deaths & he needs to be destroyed before it happens. The monkey has got to go.. If The Man with the Yellow Hat isn't able to do it himself, he should turn the monkey over to the people and let them handle it. And after it's done The Man with the Yellow Hat should at the very least be fined and made to do community service. And If he resists he can follow Curious George into death.",
		isMe: true,
		timeStamp: 00003
		)
];

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
												fontSize: 16,
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
							child: renderMessages(testMessages),
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
                        setState(() {
                          if (messageController.text != "")
                          {
                            testMessages.add(Message(msgId: uuid.v7(), toUId: widget.user.uid,msg: messageController.text, isMe: true, timeStamp: DateTime.now().millisecondsSinceEpoch));
                            messageController.clear();
                          }

						  Future.delayed(const Duration(seconds: 1), (){
                          if (mounted)
                          {
                            setState(() {
                              testMessages.add(Message(msgId:'006',toUId: '001',msg: "I am replying to you bro", isMe: false, timeStamp: DateTime.now().millisecondsSinceEpoch));
                            });
                          }
                        });
                        });
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
}