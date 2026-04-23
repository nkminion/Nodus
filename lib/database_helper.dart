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
      version: 2,
      onConfigure: (db) async {
        await db.execute('PRAGMA foreign_keys = ON');
      },
      onCreate: (db, version) async {
        await _createDB(db, version);
      },
      onUpgrade: (db, oldVersion, newVersion) async {
        if (oldVersion < 2) {
          await db.execute('ALTER TABLE Contacts ADD COLUMN PublicKey TEXT');
        }
      },
    );
  }

  Future _createDB(Database db, int version) async
  {
    await db.execute('''
      CREATE TABLE Contacts (
        UID TEXT PRIMARY KEY,
        DispName Text,
        PublicKey TEXT
      )
    ''');

    await db.execute('''
      CREATE TABLE Messages (
        MsgID TEXT PRIMARY KEY,
        FromUID TEXT,
        ToUID TEXT,
        Msg TEXT,
        TimeStamp INTEGER,
        Status INTEGER,
        FOREIGN KEY (ToUID) REFERENCES Contacts (UID),
        FOREIGN KEY (FromUID) REFERENCES Contacts (UID)
      )
    ''');
  }

  Future insertContact(String uid,String dispName, {String? publicKey}) async
  {
    Database db = await database;

    User? existing = await fetchContact(uid);
    if (existing != null) {
      Map<String, dynamic> updateData = {'DispName': dispName};
      if (publicKey != null) {
        updateData['PublicKey'] = publicKey;
      }
      await db.update('Contacts', updateData, where: 'UID = ?', whereArgs: [uid]);
    } else {
      Map<String, dynamic> row = {
        'UID': uid,
        'DispName': dispName,
      };
      if (publicKey != null) {
        row['PublicKey'] = publicKey;
      }
      await db.insert('Contacts', row, conflictAlgorithm: ConflictAlgorithm.replace);
    }
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

  Future updateStatus(String msgID) async
  {
    Database db = await database;

    await db.update(
      'Messages',
      {'Status':1},
      where: 'MsgID = ?',
      whereArgs: [msgID],
    );
  }

  Future<Map<String,Message>> fetchMessages(String myUID, String receiverUID) async
  {
    Database db = await database;

    List<Map<String,dynamic>> historyQuery = await db.query(
      'Messages',
      where: '(FromUID = ? AND ToUID = ?)OR(FromUID = ? AND ToUID = ?)',
      whereArgs: [myUID,receiverUID,receiverUID,myUID],
      orderBy: 'TimeStamp',
    );

    Map<String,Message> messageHistory = {};
    for (Map<String,dynamic> histItem in historyQuery)
    {
      Message msg = Message.fromJson(histItem);
      messageHistory[msg.msgId] = msg;
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

  Future<User?> fetchContact(String uid) async
  {
    Database db = await database;

    List<Map<String,dynamic>> contactQuery = await db.query(
      'Contacts',
      where: 'UID = ?',
      whereArgs: [uid],
    );

    if (contactQuery.isNotEmpty) {
      return User.fromJson(contactQuery.first);
    }
    return null;
  }
}