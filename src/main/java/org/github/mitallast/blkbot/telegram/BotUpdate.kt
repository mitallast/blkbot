package org.github.mitallast.blkbot.telegram

import com.fasterxml.jackson.annotation.JsonProperty
import io.vavr.control.Option

data class BotCallbackQuery(
    @JsonProperty("id") val id: String, // Unique identifier for this query
    @JsonProperty("from") val from: BotUser, // Sender
    @JsonProperty("message") val message: Option<BotMessage> = Option.none(),
    @JsonProperty("inline_message_id") val inlineMessageId: Option<String> = Option.none(),
    @JsonProperty("chat_instance") val chatInstance: String,
    @JsonProperty("data") val data: Option<String> = Option.none(),
    @JsonProperty("game_short_name") val gameShortName: Option<String> = Option.none()
)

data class BotUpdate(
    @JsonProperty("update_id") val updateId: Long,
    @JsonProperty("message") val message: Option<BotMessage> = Option.none(),
    @JsonProperty("edited_message") val editedMessage: Option<BotMessage> = Option.none(),
    @JsonProperty("channel_post") val channelPost: Option<BotMessage> = Option.none(),
    @JsonProperty("edited_channel_post") val editedChannelPost: Option<BotMessage> = Option.none(),
    @JsonProperty("inline_query") val inlineQuery: Option<Any> = Option.none(),
    @JsonProperty("chosed_inline_result") val chosenInlineResult: Option<Any> = Option.none(),
    @JsonProperty("callback_query") val callbackQuery: Option<BotCallbackQuery> = Option.none(),
    @JsonProperty("shipping_query") val shippingQuery: Option<Any> = Option.none(),
    @JsonProperty("pre_checkout_query") val preCheckoutQuery: Option<Any> = Option.none()
)