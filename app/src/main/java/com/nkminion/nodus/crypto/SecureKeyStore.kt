package com.nkminion.nodus.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import androidx.core.content.edit
import org.json.JSONObject

class SecureKeyStore(context: Context)
{
	private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
	private val masterKeyAlias = "NodusMasterKey"
	private val identityKeyAlias = "NodusIdentitySigningKey"
	private val prefs = context.getSharedPreferences("NodusKeysVault", Context.MODE_PRIVATE)

	init
	{
		ensureMasterKeyExists()
		ensureIdentityKeyExists()
	}

	private fun ensureMasterKeyExists()
	{
		if (!keyStore.containsAlias(masterKeyAlias))
		{
			val keyGen = KeyGenerator.getInstance(
				KeyProperties.KEY_ALGORITHM_AES,
				"AndroidKeyStore"
			)
			val spec = KeyGenParameterSpec.Builder(
				masterKeyAlias,
				KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
			)
			.setBlockModes(KeyProperties.BLOCK_MODE_GCM)
			.setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
			.setKeySize(256)
			.build()

			keyGen.init(spec)
			keyGen.generateKey()
		}
	}

	private fun ensureIdentityKeyExists()
	{
		if (!keyStore.containsAlias(identityKeyAlias))
		{
			val keyPairGen = KeyPairGenerator.getInstance(
				KeyProperties.KEY_ALGORITHM_EC,
				"AndroidKeyStore"
			)
			val spec = KeyGenParameterSpec.Builder(
				identityKeyAlias,
				KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
			)
				.setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
				.setDigests(KeyProperties.DIGEST_SHA256)
				.build()

			keyPairGen.initialize(spec)
			keyPairGen.generateKeyPair()
		}
	}

	private fun getMasterKey(): SecretKey
	{
		return keyStore.getKey(masterKeyAlias, null) as SecretKey
	}

	fun getMyIdentityPublicKeyBytes(): ByteArray
	{
		return keyStore.getCertificate(identityKeyAlias).publicKey.encoded
	}

	fun signData(data: ByteArray): ByteArray
	{
		val privateKey = keyStore.getKey(identityKeyAlias,null) as PrivateKey
		val signature = Signature.getInstance("SHA256withECDSA")
		signature.initSign(privateKey)
		signature.update(data)
		return signature.sign()
	}

	fun storeSessionKey(peerUuid: String, sessionKeyBytes: ByteArray)
	{
		encryptAndSave("peer_$peerUuid", sessionKeyBytes)
	}

	fun storeRatchetState(peerUuid: String, state: RatchetState)
	{
		val json = JSONObject().apply {
			put("dhsPriv", Base64.encodeToString(state.dhsPrivate, Base64.NO_WRAP))
			put("dhsPub", Base64.encodeToString(state.dhsPublic, Base64.NO_WRAP))
			put("dhrPub", state.dhrPublic?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: JSONObject.NULL)
			put("rk", Base64.encodeToString(state.rootKey, Base64.NO_WRAP))
			put("cks", state.sendChainKey?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: JSONObject.NULL)
			put("ckr", state.recvChainKey?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: JSONObject.NULL)
			put("ns", state.sendCount)
			put("nr", state.recvCount)
			put("pn", state.prevChainLen)
			val skippedJson = JSONObject()
			state.skippedKeys.forEach { (k, v) -> skippedJson.put(k, Base64.encodeToString(v, Base64.NO_WRAP)) }
			put("skipped", skippedJson)
			}
		encryptAndSave("ratchet_$peerUuid", json.toString().toByteArray(Charsets.UTF_8))
	}

	fun getRatchetState(peerUuid: String): RatchetState?
    {
		val raw = loadAndDecrypt("ratchet_$peerUuid") ?: return null
		val json = JSONObject(String(raw, Charsets.UTF_8))
		val skippedJson = json.optJSONObject("skipped") ?: JSONObject()
		val skippedMap = mutableMapOf<String, ByteArray>()
		skippedJson.keys().forEach { key -> skippedMap[key] = Base64.decode(skippedJson.getString(key), Base64.NO_WRAP) }

		return RatchetState(
			dhsPrivate = Base64.decode(json.getString("dhsPriv"), Base64.NO_WRAP),
			dhsPublic = Base64.decode(json.getString("dhsPub"), Base64.NO_WRAP),
			dhrPublic = if (json.isNull("dhrPub")) null else Base64.decode(json.getString("dhrPub"), Base64.NO_WRAP),
			rootKey = Base64.decode(json.getString("rk"), Base64.NO_WRAP),
			sendChainKey = if (json.isNull("cks")) null else Base64.decode(json.getString("cks"), Base64.NO_WRAP),
			recvChainKey = if (json.isNull("ckr")) null else Base64.decode(json.getString("ckr"), Base64.NO_WRAP),
			sendCount = json.getInt("ns"),
			recvCount = json.getInt("nr"),
			prevChainLen = json.getInt("pn"),
			skippedKeys = skippedMap
		)
	}

	private fun encryptAndSave(prefKey: String, data: ByteArray)
	{
		val cipher = Cipher.getInstance("AES/GCM/NoPadding")
		cipher.init(Cipher.ENCRYPT_MODE, getMasterKey())

		val iv = cipher.iv
		val encryptedBytes = cipher.doFinal(data)
		val payload = Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
			Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)

		prefs.edit { putString(prefKey, payload) }
	}

	private fun loadAndDecrypt(prefKey: String): ByteArray?
	{
		val payload = prefs.getString(prefKey, null) ?: return null
		val parts = payload.split(":")
		if (parts.size != 2) return null

		val iv = Base64.decode(parts[0], Base64.NO_WRAP)
		val encryptedBytes = Base64.decode(parts[1], Base64.NO_WRAP)

		val cipher = Cipher.getInstance("AES/GCM/NoPadding")
		cipher.init(Cipher.DECRYPT_MODE, getMasterKey(), GCMParameterSpec(128, iv))

		return cipher.doFinal(encryptedBytes)
	}
}