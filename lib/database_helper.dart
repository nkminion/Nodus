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
        ToUID TEXT,
        Msg TEXT,
        IsMe INTEGER,
        TimeStamp INTEGER,
        FOREIGN KEY (ToUID) REFERENCES Contacts (UID)
      )
    ''');

    await db.rawInsert(
      'INSERT INTO Contacts(UID,DispName) VALUES(?,?)',['MyUID','Me']
    );
  }
}