package com.nkminion.nodus.data

enum class PayloadType(val code: Byte)
{
	KeyExchange(0x01),
	VerifyChallenge(0x02),
	VerifyResponse(0x03),
	ChatMessage(0x04),
	ChatAck(0x05),
	IdentityAnnounce(0x06);

	companion object
	{
		fun fromByte(typeCode: Byte): PayloadType? =
			entries.firstOrNull { it.code == typeCode }
	}
}