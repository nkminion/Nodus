import 'package:flutter/material.dart';
import 'package:nodus/chat_page.dart';
import 'package:nodus/connection_manager.dart';
import 'package:nodus/util_classes.dart';

class NavigationPage extends StatefulWidget
{
	const NavigationPage({super.key, required this.dispName});
	final String dispName;

  @override
  State<NavigationPage> createState() => _NavigationPageState();
}



class _NavigationPageState extends State<NavigationPage> {
	int currentState = 0;
	
  void initState()
  {
    super.initState();
    ConnectionManager.instance.init(widget.dispName);
  }

	Widget buildList(List<User> list)
	{
		return ListView.builder(
			itemCount: list.length,
			itemBuilder: (context,index)
			{
				final user = list[index];
				return Card(
					child: 
						ListTile(
							leading: CircleAvatar(
								backgroundColor: Colors.deepPurpleAccent,
								child: Text(user.dispName[0]),
							),
							title: Text(user.dispName),
							subtitle: Text(user.hops < 4 ? 'Connected' : 'Offline'),
							trailing: Icon(
								Icons.circle,
								color:	user.hops < 2 ? Colors.green : user.hops < 4 ? Colors.amber : Colors.red,
								size: 10,
							),
							onTap: (){Navigator.of(context).push(
								MaterialPageRoute<void>(
									builder: (context) => ChatPage(user: user)
								)
							);},
						),
				);
			},
		);
	}

	Widget currentList()
	{
		if (currentState == 0)
		{
			return ValueListenableBuilder<List<User>>(
				valueListenable: ConnectionManager.instance.connectedNodes,
				builder: (context,users,child) {
					if (users.isEmpty)
					{
						return const Center(child: Text("Scanning for nodes..."),);
					}

					return buildList(users);
				},
			);
		}
		else
		{
			return const Center(child: Text("I need to integrate the database lmao"));
		}
	}

	@override
	Widget build(BuildContext context)
	{
		return Scaffold(
			appBar: AppBar(
				title: Center(child:Text(widget.dispName)),
				backgroundColor: Colors.black,
				foregroundColor: const Color.fromARGB(255, 214, 237, 255),
			),
			body: Center(
				child: Column(
					children: [
						Row(
							children: [
								Expanded(
									child:TextButton(
										style: TextButton.styleFrom(
											overlayColor:const Color.fromARGB(51, 214, 237, 255),
											minimumSize: const Size(double.infinity, 50),
											shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
											foregroundColor: const Color.fromARGB(255, 214, 237, 255),
										),
										onPressed: (){setState((){currentState = 0;});},
										child: Text(
											'Nearby'
										),
									),
								),
								Expanded(
									child:TextButton(
										style: TextButton.styleFrom(
											overlayColor:const Color.fromARGB(51, 214, 237, 255),
											minimumSize: const Size(double.infinity, 50),
											shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
											foregroundColor: const Color.fromARGB(255, 214, 237, 255),
										),
										onPressed: (){setState(() {currentState = 1;});},
										child: Text(
											'Contacts'
										),
									)
								),
							],
						),
						Expanded(child: currentList()),
					],
				),
			),
		);
	}
}