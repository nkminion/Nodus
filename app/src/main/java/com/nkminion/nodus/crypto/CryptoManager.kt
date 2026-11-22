package com.nkminion.nodus.crypto

import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

data class RatchetState(
	val dhsPrivate: ByteArray,
	val dhsPublic: ByteArray,
	val dhrPublic: ByteArray?,
	val rootKey: ByteArray,
	val sendChainKey: ByteArray?,
	val recvChainKey: ByteArray?,
	val sendCount: Int = 0,
	val recvCount: Int = 0,
	val prevChainLen: Int = 0,
	val skippedKeys: MutableMap<String, ByteArray> = mutableMapOf()
)

object CryptoManager
{
	private const val MAX_SKIP = 50
	const val CHAR_LIMIT = 256
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

	fun verifySignature(
		publicKeyBytes: ByteArray,
		data: ByteArray,
		signatureBytes: ByteArray
	): Boolean
	{
		return try {
			val keyFactory = KeyFactory.getInstance("EC")
			val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))

			val signature = Signature.getInstance("SHA256withECDSA")
			signature.initVerify(publicKey)
			signature.update(data)
			signature.verify(signatureBytes)
		}
		catch (e: Exception)
		{
			false
		}
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

	private fun deriveSharedSecretFromBytes(myPrivateKeyBytes: ByteArray, peerPublicKeyBytes: ByteArray): ByteArray
	{
		val keyFactory = KeyFactory.getInstance("EC")
		val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(myPrivateKeyBytes))
		return deriveSharedSecret(privateKey, peerPublicKeyBytes)
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

	fun hkdfSha256(salt: ByteArray, ikm: ByteArray, info: ByteArray, length: Int = 64): ByteArray
	{
		val mac = Mac.getInstance("HmacSHA256")
		val actualSalt = if (salt.isEmpty()) ByteArray(32) else salt
		mac.init(SecretKeySpec(actualSalt, "HmacSHA256"))
		val prk = mac.doFinal(ikm)

		val okm = ByteArray(length)
		var t = ByteArray(0)
		var offset = 0
		var counter: Byte = 1

		while (offset < length)
		{
			mac.init(SecretKeySpec(prk, "HmacSHA256"))
			mac.update(t)
			mac.update(info)
			mac.update(counter)
			t = mac.doFinal()

			val toCopy = minOf(t.size, length - offset)
			System.arraycopy(t, 0, okm, offset, toCopy)
			offset += toCopy
			counter++
		}
		return okm
	}
	private fun kdfRk(rootKey: ByteArray, dhOut: ByteArray): Pair<ByteArray, ByteArray>
    {
		val derived = hkdfSha256(rootKey, dhOut, "NodusRatchetRK".toByteArray(), 64)
		return derived.copyOfRange(0, 32) to derived.copyOfRange(32, 64)
	}

	private fun kdfCk(chainKey: ByteArray): Pair<ByteArray, ByteArray>
    {
		val mac = Mac.getInstance("HmacSHA256")
		mac.init(SecretKeySpec(chainKey, "HmacSHA256"))
		val nextCk = mac.doFinal(byteArrayOf(0x01))

		mac.init(SecretKeySpec(chainKey, "HmacSHA256"))
		val msgKey = mac.doFinal(byteArrayOf(0x02))
		return nextCk to msgKey
	}

	fun initRatchetSymmetric(
		sharedSecret: ByteArray,
		myInitialEcdhKeyPair: KeyPair,
		peerInitialEcdhPub: ByteArray,
		isLexicographicallySmaller: Boolean
	): RatchetState
	{
		val derived = hkdfSha256(ByteArray(32), sharedSecret, "NodusRatchetInit".toByteArray(), 96)
		val rk = derived.copyOfRange(0, 32)
		val chainSmallToLarge = derived.copyOfRange(32, 64)
		val chainLargeToSmall = derived.copyOfRange(64, 96)

		return RatchetState(
			dhsPrivate = myInitialEcdhKeyPair.private.encoded,
			dhsPublic = myInitialEcdhKeyPair.public.encoded,
			dhrPublic = peerInitialEcdhPub,
			rootKey = rk,
			sendChainKey = if (isLexicographicallySmaller) chainSmallToLarge else chainLargeToSmall,
			recvChainKey = if (isLexicographicallySmaller) chainLargeToSmall else chainSmallToLarge
		)
	}

	fun ratchetEncrypt(state: RatchetState, plaintext: String): Pair<RatchetState, ByteArray>
    {
		var workingState = state
		if (workingState.recvCount > 0 && workingState.dhrPublic != null)
		{
			val newDhs = generateEcKeyPair()
			val dhSend = deriveSharedSecret(newDhs.private, workingState.dhrPublic)
			val (rk, cks) = kdfRk(workingState.rootKey, dhSend)
			workingState = workingState.copy(
				dhsPrivate = newDhs.private.encoded,
				dhsPublic = newDhs.public.encoded,
				rootKey = rk,
				sendChainKey = cks,
				prevChainLen = workingState.sendCount,
				sendCount = 0,
				recvCount = 0
			)
		}

		val currentCks = workingState.sendChainKey ?: throw IllegalStateException("Send chain not initialized")
		val (nextCks, msgKey) = kdfCk(currentCks)
		val plaintextBytes = plaintext.take(CHAR_LIMIT).toByteArray(Charsets.UTF_8)

		val cipher = Cipher.getInstance("AES/GCM/NoPadding")
		cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(msgKey, "AES"))
		val iv = cipher.iv
		val ciphertext = cipher.doFinal(plaintextBytes)

		val dhPub = workingState.dhsPublic
		val buffer = ByteBuffer.allocate(2 + dhPub.size + 4 + 4 + iv.size + ciphertext.size)
		buffer.putShort(dhPub.size.toShort())
		buffer.put(dhPub)
		buffer.putInt(workingState.prevChainLen)
		buffer.putInt(workingState.sendCount)
		buffer.put(iv)
		buffer.put(ciphertext)

		return workingState.copy(sendChainKey = nextCks, sendCount = workingState.sendCount + 1) to buffer.array()
	}

	fun ratchetDecrypt(state: RatchetState, packetBytes: ByteArray): Pair<RatchetState, String>
    {
		val buffer = ByteBuffer.wrap(packetBytes)
		val dhLen = buffer.short.toInt() and 0xFFFF
		val headerDhPub = ByteArray(dhLen).also { buffer.get(it) }
		val prevChainLen = buffer.int
		val msgNum = buffer.int
		val iv = ByteArray(12).also { buffer.get(it) }
		val ciphertext = ByteArray(buffer.remaining()).also { buffer.get(it) }

		var workingState = state
		val skippedId = "${Base64.encodeToString(headerDhPub, Base64.NO_WRAP)}:$msgNum"

		workingState.skippedKeys.remove(skippedId)?.let { skippedMk ->
			return workingState to decryptWithKey(skippedMk, iv, ciphertext)
		}

		if (workingState.dhrPublic == null || !workingState.dhrPublic.contentEquals(headerDhPub))
		{
			workingState = skipMessageKeys(workingState, prevChainLen)
			workingState = dhRatchetStep(workingState, headerDhPub)
		}

		workingState = skipMessageKeys(workingState, msgNum)
		val currentCkr = workingState.recvChainKey ?: throw IllegalStateException("Receive chain not initialized")
		val (nextCkr, msgKey) = kdfCk(currentCkr)
		val plaintext = decryptWithKey(msgKey, iv, ciphertext)

		return workingState.copy(recvChainKey = nextCkr, recvCount = workingState.recvCount + 1) to plaintext
	}

	private fun skipMessageKeys(state: RatchetState, until: Int): RatchetState
	{
		var ckr = state.recvChainKey ?: return state
        var nr = state.recvCount
		if (until - nr > MAX_SKIP) throw SecurityException("Too many skipped messages")
		val dhr = state.dhrPublic ?: return state
		val dhrB64 = Base64.encodeToString(dhr, Base64.NO_WRAP)
		val newSkipped = state.skippedKeys.toMutableMap()

		while (nr < until)
		{
			val (nextCk, mk) = kdfCk(ckr)
			newSkipped["$dhrB64:$nr"] = mk
			ckr = nextCk
			nr++
		}
		return state.copy(recvChainKey = ckr, recvCount = nr, skippedKeys = newSkipped)
	}

	private fun dhRatchetStep(state: RatchetState, peerNewDhPub: ByteArray): RatchetState
	{
		val dhRecv = deriveSharedSecretFromBytes(state.dhsPrivate, peerNewDhPub)
		val (rk1, ckr) = kdfRk(state.rootKey, dhRecv)

		val newDhs = generateEcKeyPair()
		val dhSend = deriveSharedSecret(newDhs.private, peerNewDhPub)
		val (rk2, cks) = kdfRk(rk1, dhSend)

		return state.copy(
			dhsPrivate = newDhs.private.encoded,
			dhsPublic = newDhs.public.encoded,
			dhrPublic = peerNewDhPub,
			rootKey = rk2,
			sendChainKey = cks,
			recvChainKey = ckr,
			prevChainLen = state.sendCount,
			sendCount = 0,
			recvCount = 0
		)
	}

	private fun decryptWithKey(msgKey: ByteArray, iv: ByteArray, ciphertext: ByteArray): String
	{
		val cipher = Cipher.getInstance("AES/GCM/NoPadding")
		cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(msgKey, "AES"), GCMParameterSpec(128, iv))
		return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
	}
}