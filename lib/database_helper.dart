import 'package:nodus/util_classes.dart';
import 'package:sqflite/sqflite.dart';
import 'dart:async';
import 'package:path/path.dart';
import 'package:path_provider/path_provider.dart';
import 'dart:io';

class DatabaseHelper
{
  static final DatabaseHelper instance = DatabaseHelper._();
  DatabaseHelper._();

  static Database? _database;
  Future<Database> get database async
  {
    if (_database != null)
    {
      return _database!;
    }
    
    _database = await _initDB('nodus.db');

    return _database!;
  }

  Future<Database> _initDB(String fileName) async
  {
    Directory folderPath = await getApplicationDocumentsDirectory();
    String path = join(folderPath.path,fileName);

    return await openDatabase(
      path,
      version: 1,
      onConfigure: (db) async {
        await db.execute('PRAGMA foreign_keys = ON');
      },
      onCreate: (db, version) async {
        await _createDB(db, version);
      },
    );
  }

  Future _createDB(Database db, int version) async
  {
    await db.execute('''
      CREATE TABLE Contacts (
        UID TEXT PRIMARY KEY,
        DispName Text
      )
    ''');

    await db.execute('''
      CREATE TABLE Messages (
        MsgID TEXT PRIMARY KEY,
        FromUID TEXT,
        ToUID TEXT,
        Msg TEXT,
        TimeStamp INTEGER,
        FOREIGN KEY (ToUID) REFERENCES Contacts (UID),
        FOREIGN KEY (FromUID) REFERENCES Contacts (UID)
      )
    ''');
  }

  Future insertContact(String uid,String dispName) async
  {
    Database db = await database;

    await db.insert(
      'Contacts',
      {
        'UID':uid,
        'DispName':dispName,
      },
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }
  

  Future insertMessage(Message msg) async
  {
    Database db = await database;

    await db.insert(
      'Messages',
      msg.toMap(),
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  Future<List<Message>> fetchMessages(String myUID, String receiverUID) async
  {
    Database db = await database;

    List<Map<String,dynamic>> historyQuery = await db.query(
      'Messages',
      where: '(FromUID = ? AND ToUID = ?)OR(FromUID = ? AND ToUID = ?)',
      whereArgs: [myUID,receiverUID,receiverUID,myUID],
      orderBy: 'TimeStamp',
    );

    List<Message> messageHistory = [];
    for (Map<String,dynamic> histItem in historyQuery)
    {
      messageHistory.add(Message.fromJson(histItem));
    }
    return messageHistory;
  }

  Future<List<User>> fetchContacts(String myUID) async
  {
    Database db = await database;

    List<Map<String,dynamic>> contactQuery = await db.query(
      'Contacts',
      where: 'UID != ?',
      whereArgs: [myUID],
    );

    List<User> contacts = [];
    for (Map<String,dynamic> contact in contactQuery)
    {
      contacts.add(User.fromJson(contact));
    }
    return contacts;
  }
}