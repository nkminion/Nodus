import 'package:flutter/material.dart';
import 'package:nodus/chat_page.dart';

class User
{
	final String id;
	final String displayName;
	final bool reachable;

	User({required this.id, required this.displayName, this.reachable=true});
}

final List<User> nearbyUsers = [
	User(id: '001', displayName: 'ArchUser'),
	User(id: '002', displayName: 'SkibidiBoy'),
	User(id: '003', displayName: 'Penguin', reachable: false),
];

class NavigationPage extends StatefulWidget
{
	const NavigationPage({super.key, required this.displayName});
	final String displayName;

  @override
  State<NavigationPage> createState() => _NavigationPageState();
}



class _NavigationPageState extends State<NavigationPage> {
	int currentState = 0;
	
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
								child: Text(user.displayName[0]),
							),
							title: Text(user.displayName),
							subtitle: Text(user.reachable ? 'Connected' : 'Offline'),
							trailing: Icon(
								Icons.circle,
								color:	user.reachable ? Colors.green : Colors.red,
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
			return buildList(nearbyUsers.where((u) => u.reachable==true).toList());
		}
		else
		{
			return buildList(nearbyUsers);
		}
	}

	@override
	Widget build(BuildContext context)
	{
		return Scaffold(
			appBar: AppBar(
				title: Center(child:Text(widget.displayName)),
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
											'History'
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