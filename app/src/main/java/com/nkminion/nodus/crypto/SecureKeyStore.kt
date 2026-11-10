package com.nkminion.nodus.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import androidx.core.content.edit

class SecureKeyStore(context: Context)
{
	private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
	private val masterKeyAlias = "NodusMasterKey"
	private val prefs = context.getSharedPreferences("NodusKeysVault", Context.MODE_PRIVATE)

	init
	{
		ensureMasterKeyExists()
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

	private fun getMasterKey(): SecretKey
	{
		return keyStore.getKey(masterKeyAlias, null) as SecretKey
	}

	fun storeSessionKey(peerUuid: String, sessionKeyBytes: ByteArray)
	{
		val cipher = Cipher.getInstance("AES/GCM/NoPadding")
		cipher.init(Cipher.ENCRYPT_MODE, getMasterKey())

		val iv = cipher.iv
		val encryptedBytes = cipher.doFinal(sessionKeyBytes)

		val payload = Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
				Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)

		prefs.edit { putString("peer_$peerUuid", payload) }
	}

	fun getSessionKey(peerUuid: String): ByteArray?
	{
		val payload = prefs.getString("peer_$peerUuid", null) ?: return null
		val parts = payload.split(":")
		if (parts.size != 2) return null

		val iv = Base64.decode(parts[0], Base64.NO_WRAP)
		val encryptedBytes = Base64.decode(parts[1], Base64.NO_WRAP)

		val cipher = Cipher.getInstance("AES/GCM/NoPadding")
		cipher.init(Cipher.DECRYPT_MODE, getMasterKey(), GCMParameterSpec(128, iv))

		return cipher.doFinal(encryptedBytes)
	}

	fun hasKey(peerUuid: String): Boolean = prefs.contains("peer_$peerUuid")
}