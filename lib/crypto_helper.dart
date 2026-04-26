import 'dart:convert';
import 'package:cryptography/cryptography.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'database_helper.dart';

class CryptoHelper 
{
  static final CryptoHelper instance = CryptoHelper._();
  CryptoHelper._();

  SimpleKeyPair? _keyPair;
  String? _publicKeyBase64;

  String? get publicKeyBase64 => _publicKeyBase64;

  Future<void> initOrCreate() async 
  {
    final prefs = await SharedPreferences.getInstance();
    String? privKeyStr = prefs.getString('PrivKey');
    String? pubKeyStr = prefs.getString('PubKey');

    final algorithm = X25519();

    if (privKeyStr == null || pubKeyStr == null) 
    {
      //Generate new keypair
      _keyPair = await algorithm.newKeyPair();
      final pubKey = await _keyPair!.extractPublicKey();
      
      _publicKeyBase64 = base64Encode(pubKey.bytes);
      
      final privateKeyBytes = await _keyPair!.extractPrivateKeyBytes();
      await prefs.setString('PrivKey', base64Encode(privateKeyBytes));
      await prefs.setString('PubKey', _publicKeyBase64!);
    } 
    else 
    {
      //Load existing keypair
      final privBytes = base64Decode(privKeyStr);
      final pubBytes = base64Decode(pubKeyStr);
      
      _keyPair = SimpleKeyPairData(
        privBytes,
        publicKey: SimplePublicKey(pubBytes, type: KeyPairType.x25519),
        type: KeyPairType.x25519,
      );
      _publicKeyBase64 = pubKeyStr;
    }
  }

  Future<SecretKey> _deriveAESKey(String peerPublicKeyBase64) async 
  {
    if (_keyPair == null) 
    {
      throw Exception("CryptoHelper not initialized");
    }
    final peerPubBytes = base64Decode(peerPublicKeyBase64);
    final peerPublicKey = SimplePublicKey(peerPubBytes, type: KeyPairType.x25519);

    final algorithm = X25519();
    final sharedSecret = await algorithm.sharedSecretKey(
      keyPair: _keyPair!,
      remotePublicKey: peerPublicKey,
    );

    //HKDF with SHA-256
    final hkdf = Hkdf(
      hmac: Hmac.sha256(),
      outputLength: 32, //AES-GCM 256 uses 32 bytes
    );
    
    final derivedKey = await hkdf.deriveKey(
      secretKey: sharedSecret,
      nonce: [],
    );

    return derivedKey;
  }

  Future<Map<String, String>> encryptMessage(Map<String,dynamic> payload, String peerPublicKeyBase64) async 
  {
    final derivedKey = await _deriveAESKey(peerPublicKeyBase64);
    final aesGcm = AesGcm.with256bits();
    
    final secretBox = await aesGcm.encrypt(
      utf8.encode(jsonEncode(payload)),
      secretKey: derivedKey,
    );

    return {
      'payload': base64Encode(secretBox.cipherText),
      'mac': base64Encode(secretBox.mac.bytes),
      'nonce': base64Encode(secretBox.nonce),
    };
  }

  Future<Map<String,dynamic>?> decryptMessage(String payload, String nonceBase64, String macBase64) async 
  {
    Map<String,String> contactKeys = await DatabaseHelper.instance.fetchKeys();

    for (String key in contactKeys.keys)
    {
      try
      {
        final derivedKey = await _deriveAESKey(key);
        final aesGcm = AesGcm.with256bits();
        
        final secretBox = SecretBox(
          base64Decode(payload),
          nonce: base64Decode(nonceBase64),
          mac: Mac(base64Decode(macBase64)),
        );

        final decryptedPayload = await aesGcm.decrypt(
          secretBox,
          secretKey: derivedKey,
        );

        final payloadJson = json.decode(utf8.decode(decryptedPayload));

        if (payloadJson['from'] == contactKeys[key])
        {
          return payloadJson;
        }
      }
      catch (keyError)
      {
        continue;
      }
    }
    return null;
  }
}
