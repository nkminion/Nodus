package com.nkminion.nodus.data

data class TextMessage(
	val text: String,
	val isMe: Boolean,
	val timestamp: String
)

val sampleMessages = listOf(
	TextMessage(text = "Mambo", isMe = true, timestamp = "6:09AM"),
	TextMessage(text = "WOAHHH", isMe = false, timestamp = "7:11AM"),
	TextMessage(text = "DUANNGGGG", isMe = true, timestamp = "9:11AM"),
	TextMessage(
		text = "The monkey causes more problems than he's worth. His curiosity has hurt or at the very least made peoples lives more difficult just because The Man with the Yellow Hat can't be bothered to control him. After this many “lessons” the damn monkey has had after fucking everyone over, it's clear he's not gonna learn. He's gonna keep fucking up. And we're all made to pay the price just because The Man with the Yellow Hat has no sense of decency. Eventually the monkey will end up causing a death or many deaths & he needs to be destroyed before it happens. The monkey has got to go.. If The Man with the Yellow Hat isn't able to do it himself, he should turn the monkey over to the people and let them handle it. And after it's done The Man with the Yellow Hat should at the very least be fined and made to do community service. And If he resists he can follow Curious George into death.",
		isMe = true,
		timestamp = "4:20PM"
	)
)