import 'package:flutter/material.dart';
import 'login_page.dart';

void main()
{
  runApp(const App());
}

class App extends StatelessWidget
{
  const App({super.key});

  @override
  Widget build(BuildContext context)
  {
	return MaterialApp(
	  title: "Nodus",
	  theme: ThemeData(
		brightness: Brightness.dark,
		primaryColor: Colors.blue,
		scaffoldBackgroundColor: Color.fromARGB(255,24,24,24),
		
	  ),
	  home: const LoginPage(title: 'Login Page'),
	);
  }
}



