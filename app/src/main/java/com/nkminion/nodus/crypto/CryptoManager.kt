package com.nkminion.nodus.crypto

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import java.util.Locale
import javax.crypto.KeyAgreement
import kotlin.math.abs

object CryptoManager
{
	private fun compareByteArrays(a: ByteArray, b: ByteArray): Int
	{
		val minLen = minOf(a.size, b.size)
		for (i in 0 until minLen) {
			val aByte = a[i].toInt() and 0xFF
			val bByte = b[i].toInt() and 0xFF
			if (aByte != bByte) return aByte - bByte
		}
		return a.size - b.size
	}
	fun generateEcKeyPair(): KeyPair
	{
		val keyPairGen = KeyPairGenerator.getInstance("EC")
		keyPairGen.initialize(256)
		return keyPairGen.generateKeyPair()
	}

	fun deriveSharedSecret(myPrivateKey: java.security.PrivateKey, peerPublicKeyBytes: ByteArray): ByteArray
	{
		val keyFactory = KeyFactory.getInstance("EC")
		val peerPublicKey = keyFactory.generatePublic(X509EncodedKeySpec(peerPublicKeyBytes))

		val keyAgreement = KeyAgreement.getInstance("ECDH")
		keyAgreement.init(myPrivateKey)
		keyAgreement.doPhase(peerPublicKey, true)
		val rawSecret = keyAgreement.generateSecret()

		val sha256 = MessageDigest.getInstance("SHA-256")
		return sha256.digest(rawSecret)
	}

	fun generateSasCode(
		myPubKey: ByteArray,
		peerPubKey: ByteArray,
		sharedSecret: ByteArray
	): String
	{
		val digest = MessageDigest.getInstance("SHA-256")

		val compareResult = compareByteArrays(myPubKey, peerPubKey)
		if (compareResult <= 0) {
			digest.update(myPubKey)
			digest.update(peerPubKey)
		} else {
			digest.update(peerPubKey)
			digest.update(myPubKey)
		}
		digest.update(sharedSecret)

		val hash = digest.digest()
		val codeInt = ((hash[0].toInt() and 0xFF) shl 24) or
				((hash[1].toInt() and 0xFF) shl 16) or
				((hash[2].toInt() and 0xFF) shl 8) or
				(hash[3].toInt() and 0xFF)

		val positiveCode = abs(codeInt) % 1000000
		return String.format(Locale.US,"%06d", positiveCode)
	}
}