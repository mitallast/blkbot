package org.github.mitallast.blkbot.telegram

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.type.TypeReference
import com.typesafe.config.Config
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.*
import io.vavr.collection.Map
import io.vavr.collection.HashMap
import io.vavr.collection.Vector
import io.vavr.concurrent.Future
import io.vavr.control.Option
import org.apache.logging.log4j.LogManager
import org.github.mitallast.blkbot.common.http.HttpClient
import org.github.mitallast.blkbot.common.json.JsonService
import java.net.URI
import java.nio.charset.Charset
import javax.inject.Inject

open class TelegramException(message: String) : RuntimeException(message)
class TelegramClientException(val code: Int, message: String) : TelegramException(message)
class TelegramServerException(val code: Int, message: String) : TelegramException(message)
class TelegramUnknownException(val code: Int, message: String) : TelegramException(message)

class TelegramBotApi @Inject constructor(
        private val config: Config,
        private val json: JsonService,
        private val http: HttpClient
) {
    private val logger = LogManager.getLogger()

    private val charset = Charset.forName("UTF-8")
    private val token = config.getString("telegram.token")
    private val endpoint = URI(config.getString("telegram.endpoint"))
    private val host = endpoint.host
    private val connect = http.connect(endpoint)

    private fun send(request: HttpRequest): Future<FullHttpResponse> {
        logger.info("request ${request.uri()}")
        return connect.send(request).map { response ->
            val code = response.status().code()
            when (code) {
                in 200..299 -> {
                    logger.info("success")
                    response
                }
                in 400..499 -> {
                    val message = response.content().toString(charset)
                    response.release()
                    logger.warn(message)
                    throw TelegramClientException(code, message)
                }
                504 -> {
                    val message = response.content().toString(charset)
                    response.release()
                    logger.warn(message)
                    throw TelegramUnknownException(code, message)
                }
                in 500..599 -> {
                    val message = response.content().toString(charset)
                    response.release()
                    logger.warn(message)
                    throw TelegramServerException(code, message)
                }
                else -> {
                    val message = response.content().toString(charset)
                    response.release()
                    logger.warn(message)
                    throw TelegramUnknownException(code, message)
                }
            }
        }
    }

    private fun <T> sendJson(request: HttpRequest, type: TypeReference<BotResponse<T>>): Future<T> {
        return send(request).map { response: FullHttpResponse ->
            logger.info("deserialize {}", response.content().toString(charset))
            val mapped: BotResponse<T> = json.deserialize(response.content(), type)
            logger.info("response: {}", mapped)
            response.release()
            when {
                mapped.ok -> mapped.result
                else -> throw TelegramClientException(mapped.errorCode!!, mapped.description!!)
            }
        }
    }

    private fun <T> rpc(method: String, type: TypeReference<BotResponse<T>>): Future<T> {
        val uri = "/bot$token/$method"
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri)
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        return sendJson(request, type)
    }

    private fun <T> rpc(method: String, args: Map<String, Any>, type: TypeReference<BotResponse<T>>): Future<T> {
        if (args.isEmpty) {
            return rpc(method, type)
        }
        val params = json.serialize(args)
        val content = Unpooled.copiedBuffer(params, charset)
        val uri = "/bot$token/$method"
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, uri, content)
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE)
        request.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
        request.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes())
        return sendJson(request, type)
    }

    fun getUpdates(
            offset: Long? = null,
            limit: Long? = null,
            timeout: Long? = null,
            allowedUpdates: Vector<String>? = null
    ): Future<Vector<BotUpdate>> {
        val args = HashMap.of(
                "offset", Option.of(offset),
                "limit", Option.of(limit),
                "timeout", Option.of(timeout),
                "allowed_updates", Option.of(allowedUpdates)
        ).filterValues { v -> v.isDefined }.mapValues { v -> v.get() as Any }
        val type = object : TypeReference<BotResponse<Vector<BotUpdate>>>() {}
        return rpc("getUpdates", args, type)
    }

    fun sendMessage(
            chatId: String,
            text: String,
            parse_mode: String? = null,
            disableWebPagePreview: String? = null,
            disableNotification: String? = null,
            replyToMessageId: String? = null,
            replyMarkup: BotReplyMarkup? = null
    ): Future<BotMessage> {
        val args = HashMap.of(
                "chat_id", Option.of(chatId),
                "text", Option.of(text),
                "parse_mode", Option.of(parse_mode),
                "disable_web_page_preview", Option.of(disableWebPagePreview),
                "disable_notification", Option.of(disableNotification),
                "reply_to_message_id", Option.of(replyToMessageId),
                "reply_markup", Option.of(replyMarkup)
        ).filterValues { v -> v.isDefined }.mapValues { v -> v.get() as Any }
        val type = object : TypeReference<BotResponse<BotMessage>>() {}
        return rpc("sendMessage", args, type)
    }
}

data class BotResponse<out T>(
        @JsonProperty("ok") val ok: Boolean,
        @JsonProperty("error_code") val errorCode: Int?,
        @JsonProperty("description") val description: String?,
        @JsonProperty("result") val result: T?
)

interface BotReplyMarkup {}