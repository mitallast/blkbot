package org.github.mitallast.blkbot.telegram

import com.fasterxml.jackson.annotation.JsonProperty
import io.vavr.collection.Vector
import io.vavr.control.Option

data class BotUser(
        @JsonProperty("id") val id: Long,
        @JsonProperty("is_bot") val isBot: Boolean,
        @JsonProperty("first_name") val firstName: String,
        @JsonProperty("last_name") val lastName: Option<String> = Option.none(),
        @JsonProperty("username") val username: Option<String> = Option.none(),
        @JsonProperty("language_code") val languageCode: Option<String> = Option.none()
)

data class BotChat(
        @JsonProperty("id") val id: Long,
        @JsonProperty("type") val type: String,
        @JsonProperty("title") val title: Option<String> = Option.none(),
        @JsonProperty("username") val username: Option<String> = Option.none(),
        @JsonProperty("first_name") val firstName: Option<String> = Option.none(),
        @JsonProperty("last_name") val lastName: Option<String> = Option.none(),
        @JsonProperty("all_members_are_administrators") val allMembersAreAdministrators: Option<Boolean> = Option.none(),
        @JsonProperty("photo") val photo: Option<BotChatPhoto> = Option.none(),
        @JsonProperty("description") val description: Option<String> = Option.none(),
        @JsonProperty("invite_link") val inviteLink: Option<String> = Option.none(),
        @JsonProperty("pinned_message") val pinnedMessage: Option<BotMessage> = Option.none(),
        @JsonProperty("sticker_set_name") val stickerSetName: Option<String> = Option.none(),
        @JsonProperty("can_set_sticker_set") val canSetStickerSet: Option<Boolean> = Option.none()
)

data class BotChatPhoto(
        @JsonProperty("small_file_id") val small_file_id: String,
        @JsonProperty("big_file_id") val big_file_id: String
)

data class BotMessageEntity(
        @JsonProperty("type") val type: String,
        @JsonProperty("offset") val offset: Long,
        @JsonProperty("length") val length: Long,
        @JsonProperty("url") val url: Option<String> = Option.none(),
        @JsonProperty("user") val user: Option<BotUser> = Option.none()
)

data class BotAudio(
        @JsonProperty("file_id") val fileId: String,
        @JsonProperty("duration") val duration: Long,
        @JsonProperty("performer") val performer: Option<String> = Option.none(),
        @JsonProperty("title") val title: Option<String> = Option.none(),
        @JsonProperty("mime_type") val mimeType: Option<String> = Option.none(),
        @JsonProperty("file_size") val fileSize: Option<Long> = Option.none()
)

data class BotDocument(
        @JsonProperty("file_id") val file_id: String,
        @JsonProperty("thumb") val thumb: Option<BotPhotoSize> = Option.none(),
        @JsonProperty("file_name") val fileName: Option<String> = Option.none(),
        @JsonProperty("mime_type") val mimeType: Option<String> = Option.none(),
        @JsonProperty("file_size") val fileSize: Option<Long> = Option.none()
)

data class BotPhotoSize(
        @JsonProperty("file_id") val fileId: String,
        @JsonProperty("width") val width: Long,
        @JsonProperty("height") val height: Long,
        @JsonProperty("file_size") val fileSize: Option<Long> = Option.none()
)

data class BotVideo(
        @JsonProperty("file_id") val fileId: String,
        @JsonProperty("width") val width: Long,
        @JsonProperty("height") val height: Long,
        @JsonProperty("duration") val duration: Long,
        @JsonProperty("thumb") val thumb: Option<BotPhotoSize> = Option.none(),
        @JsonProperty("mime_type") val mimeType: Option<String> = Option.none(),
        @JsonProperty("file_size") val fileSize: Option<Long> = Option.none()
)

data class BotVoice(
        @JsonProperty("file_id") val fileId: String,
        @JsonProperty("duration") val duration: Long,
        @JsonProperty("mime_type") val mimeType: Option<String> = Option.none(),
        @JsonProperty("file_size") val fileSize: Option<Long> = Option.none()
)

data class BotVideoNote(
        @JsonProperty("file_id") val fileId: String,
        @JsonProperty("length") val length: Long,
        @JsonProperty("duration") val duration: Long,
        @JsonProperty("thumb") val thumb: Option<BotPhotoSize> = Option.none(),
        @JsonProperty("file_size") val fileSize: Option<Long> = Option.none()
)

data class BotContact(
        @JsonProperty("phone_number") val phoneNumber: String,
        @JsonProperty("first_name") val firstName: Option<String> = Option.none(),
        @JsonProperty("last_name") val lastName: Option<String> = Option.none(),
        @JsonProperty("user_id") val userId: Option<Long> = Option.none()
)

data class BotLocation(
        @JsonProperty("longitude") val longitude: Double,
        @JsonProperty("latitude") val latitude: Double
)

data class BotVenue(
        @JsonProperty("location") val location: BotLocation,
        @JsonProperty("title") val title: String,
        @JsonProperty("address") val address: String,
        @JsonProperty("foursquare_id") val foursquareId: Option<String> = Option.none()
)

data class BotMessage(
        @JsonProperty("message_id") val messageId: Long,
        @JsonProperty("from") val from: Option<BotUser> = Option.none(),
        @JsonProperty("date") val date: Long,
        @JsonProperty("chat") val chat: BotChat,
        @JsonProperty("forward_from") val forwardFrom: Option<BotUser> = Option.none(),
        @JsonProperty("forward_from_chat") val forwardFromChat: Option<BotChat> = Option.none(),
        @JsonProperty("forward_from_message_id") val forwardFromMessage_id: Option<Long> = Option.none(),
        @JsonProperty("forward_signature") val forwardSignature: Option<String> = Option.none(),
        @JsonProperty("forward_date") val forwardDate: Option<Long> = Option.none(),
        @JsonProperty("reply_to_message") val replyToMessage: Option<BotMessage> = Option.none(),
        @JsonProperty("edit_date") val editDate: Option<Long> = Option.none(),
        @JsonProperty("media_group_id") val mediaGroupId: Option<String> = Option.none(),
        @JsonProperty("author_signature") val authorSignature: Option<String> = Option.none(),
        @JsonProperty("text") val text: Option<String> = Option.none(),
        @JsonProperty("entities") val entities: Vector<BotMessageEntity> = Vector.empty(),
        @JsonProperty("caption_entities") val captionEntities: Vector<BotMessageEntity> = Vector.empty(),
        @JsonProperty("audio") val audio: Option<BotAudio> = Option.none(),
        @JsonProperty("document") val document: Option<BotDocument> = Option.none(),
        @JsonProperty("game") val game: Option<Any> = Option.none(),
        @JsonProperty("photo") val photo: Vector<BotPhotoSize> = Vector.empty(),
        @JsonProperty("sticker") val sticker: Option<Any> = Option.none(),
        @JsonProperty("video") val video: Option<BotVideo> = Option.none(),
        @JsonProperty("voice") val voice: Option<BotVoice> = Option.none(),
        @JsonProperty("video_note") val videoNote: Option<BotVideoNote> = Option.none(),
        @JsonProperty("caption") val caption: Option<String> = Option.none(),
        @JsonProperty("contact") val contact: Option<BotContact> = Option.none(),
        @JsonProperty("location") val location: Option<BotLocation> = Option.none(),
        @JsonProperty("venue") val venue: Option<BotVenue> = Option.none(),
        @JsonProperty("new_chat_members") val newChatMembers: Vector<BotUser> = Vector.empty(),
        @JsonProperty("left_chat_member") val leftChatMember: Option<BotUser> = Option.none(),
        @JsonProperty("new_chat_title") val newChatTitle: Option<String> = Option.none(),
        @JsonProperty("new_chat_photo") val newChatPhoto: Vector<BotPhotoSize> = Vector.empty(),
        @JsonProperty("delete_chat_photo") val deleteChatPhoto: Option<Boolean> = Option.none(),
        @JsonProperty("group_chat_created") val groupChatCreated: Option<Boolean> = Option.none(),
        @JsonProperty("supergroup_chat_created") val supergroupChatCreated: Option<Boolean> = Option.none(),
        @JsonProperty("channel_chat_created") val channelChatCreated: Option<Boolean> = Option.none(),
        @JsonProperty("migrate_to_chat_id") val migrateToChatId: Option<Long> = Option.none(),
        @JsonProperty("migrate_from_chat_id") val migrateFromChatId: Option<Long> = Option.none(),
        @JsonProperty("pinned_message") val pinnedMessage: Option<BotMessage> = Option.none(),
        @JsonProperty("invoice") val invoice: Option<Any> = Option.none(),
        @JsonProperty("successful_payment") val successfulPayment: Option<Any> = Option.none()
)