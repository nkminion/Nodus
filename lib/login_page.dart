import 'package:flutter/material.dart';
import 'navigation_page.dart';
import 'connection_manager.dart';
import 'crypto_helper.dart';

class LoginPage extends StatefulWidget
{
	const LoginPage({super.key, required this.title});
	final String title;

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage>
{
  @override
  void initState()
  {
    super.initState();
  }

	@override
	Widget build(BuildContext context)
	{
    bool isProcessing = false;
    final textControl = TextEditingController();
		return Scaffold(
			appBar: AppBar(
				title: Center(child:Text(widget.title)),
				backgroundColor: Colors.black,
				foregroundColor: const Color.fromARGB(255, 214, 237, 255),
			),
			body: Center(
				child: Column(
					mainAxisAlignment: MainAxisAlignment.center,
					mainAxisSize: MainAxisSize.min,
					children: [
						SizedBox(
							width: 300,
							child: TextField(
								controller: textControl,
											style: TextStyle(
												color: const Color.fromARGB(255, 214, 237, 255),
											),
							autofocus: true,
							decoration: 
								InputDecoration(
									focusedBorder: OutlineInputBorder(
										borderSide: BorderSide(color: Colors.blue, width: 2.0),
										borderRadius: BorderRadius.circular(50),
										),
									labelText: 'Display Name',
									labelStyle: TextStyle(
										color: Colors.blue,
									),
									border: OutlineInputBorder(
										borderRadius: BorderRadius.circular(50),
									),
								),
							)
						),
						const SizedBox(height: 30,),
						SizedBox(
							width: 150,
							child: ElevatedButton(
								// ignore: dead_code
								onPressed: isProcessing ? null : () async
								{
                  setState(() => isProcessing = true);
                  try
                  {
                    if (textControl.text != "")
                    {
                      await ConnectionManager.instance.loadUID();
                      await CryptoHelper.instance.initOrCreate();
                      if (!context.mounted) return;
                      Navigator.of(context).push(
                        MaterialPageRoute<void>(
                        builder: (context) => NavigationPage(dispName: textControl.text,myUID:ConnectionManager.instance.myUID!)
                        )
                      );
                    }
                  }
                  finally
                  {
                    if (mounted) setState(() => isProcessing = false);
                  }
								},
								style: ElevatedButton.styleFrom(
									backgroundColor: Colors.black,
									foregroundColor: Colors.blue,
									padding: const EdgeInsets.symmetric(horizontal: 40,vertical: 20),
									side: const BorderSide(
										color: Colors.blue,
										width: 3,
									),
									overlayColor:const Color.fromARGB(51, 214, 237, 255),
								),
								child: Text(
									'Next',
									style: const TextStyle(color: Colors.blue,),
								), 
							),
						),
					],
				),
			),
		);
	}
}