package one.mixin.android.vo

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.os.Environment.DIRECTORY_MUSIC
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.media3.common.MimeTypes
import androidx.recyclerview.widget.DiffUtil
import one.mixin.android.MixinApplication
import one.mixin.android.R
import one.mixin.android.extension.copyFromInputStream
import one.mixin.android.extension.generateConversationPath
import one.mixin.android.extension.getAudioPath
import one.mixin.android.extension.getDocumentPath
import one.mixin.android.extension.getImagePath
import one.mixin.android.extension.getPublicPicturePath
import one.mixin.android.extension.getTranscriptDirPath
import one.mixin.android.extension.getVideoPath
import one.mixin.android.extension.hasWritePermission
import one.mixin.android.extension.isFileUri
import one.mixin.android.extension.isImageSupport
import one.mixin.android.extension.toast
import one.mixin.android.util.GsonHelper
import one.mixin.android.util.VideoPlayer
import one.mixin.android.util.getLocalString
import one.mixin.android.util.reportException
import java.io.File
import java.io.FileInputStream

class ChatHistoryMessageItem(
    val transcriptId: String? = null,
    val conversationId: String? = null,
    val messageId: String,
    val userId: String?,
    val userFullName: String?,
    val userIdentityNumber: String?,
    override val type: String,
    val appId: String?,
    val content: String?,
    val createdAt: String,
    val mediaStatus: String?,
    val mediaName: String?,
    val mediaMimeType: String?,
    val mediaSize: Long?,
    val thumbUrl: String?,
    val mediaWidth: Int?,
    val mediaHeight: Int?,
    val thumbImage: String?,
    val mediaUrl: String?,
    val mediaDuration: String?,
    val mediaWaveform: ByteArray? = null,
    val assetWidth: Int? = null,
    val assetHeight: Int? = null,
    val assetUrl: String? = null,
    val assetType: String? = null,
    val sharedUserId: String? = null,
    val sharedUserFullName: String? = null,
    val sharedUserAvatarUrl: String? = null,
    val sharedUserIdentityNumber: String? = null,
    val sharedUserIsVerified: Boolean? = null,
    val sharedUserAppId: String? = null,
    val sharedMembership: Membership? = null,
    val quoteId: String? = null,
    val quoteContent: String? = null,
    val mentions: String? = null,
    val membership: Membership? = null
) : ICategory {
    companion object {
        val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<ChatHistoryMessageItem>() {
                override fun areItemsTheSame(
                    oldItem: ChatHistoryMessageItem,
                    newItem: ChatHistoryMessageItem,
                ): Boolean = oldItem.messageId == newItem.messageId

                @SuppressLint("DiffUtilEquals")
                override fun areContentsTheSame(
                    oldItem: ChatHistoryMessageItem,
                    newItem: ChatHistoryMessageItem,
                ): Boolean = oldItem == newItem
            }
    }

    val appCardData: AppCardData? by lazy {
        content?.let {
            GsonHelper.customGson.fromJson(it, AppCardData::class.java)
        }
    }

    fun isMembership() = membership?.isMembership() == true

    fun isSharedMembership() = sharedMembership?.isMembership() == true

    fun isSharedProsperity() = sharedMembership?.isProsperity() == true
}

fun ChatHistoryMessageItem.isLottie() = assetType?.equals(Sticker.STICKER_TYPE_JSON, true) == true

fun ChatHistoryMessageItem.saveToLocal(context: Context) {
    if (!hasWritePermission()) return

    val filePath = absolutePath()
    if (filePath == null) {
        reportException(IllegalStateException("Save messageItem failure, category: $type, mediaUrl: $mediaUrl, absolutePath: $filePath"))
        toast(R.string.Save_failure)
        return
    }

    val file = filePath.toUri().toFile()
    if (!file.exists()) {
        reportException(IllegalStateException("Save messageItem failure, category: $type, mediaUrl: $mediaUrl, absolutePath: $filePath"))
        toast(R.string.File_does_not_exist)
        return
    }
    val outFile =
        if (MimeTypes.isVideo(mediaMimeType) || mediaMimeType?.isImageSupport() == true) {
            File(context.getPublicPicturePath(), mediaName ?: file.name).apply {
                mkdirs()
            }
        } else {
            val dir =
                if (MimeTypes.isAudio(mediaMimeType)) {
                    context.getExternalFilesDir(DIRECTORY_MUSIC)
                } else {
                    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                } ?: return
            dir.mkdir()
            File(dir, mediaName ?: file.name)
        }
    outFile.copyFromInputStream(FileInputStream(file))
    MediaScannerConnection.scanFile(context, arrayOf(outFile.toString()), null, null)
    toast(getLocalString(MixinApplication.appContext, R.string.Save_to, outFile.absolutePath))
}

fun ChatHistoryMessageItem.loadVideoOrLive(actionAfterLoad: (() -> Unit)? = null) {
    absolutePath()?.let {
        if (isLive()) {
            VideoPlayer.player().loadHlsVideo(it, messageId)
        } else {
            VideoPlayer.player().loadVideo(it, messageId)
        }
        actionAfterLoad?.invoke()
    }
}

fun ChatHistoryMessageItem.toMessageItem(conversationId: String? = null): MessageItem {
    return MessageItem(
        messageId,
        conversationId ?: this.conversationId ?: "",
        userId ?: "",
        userFullName ?: "",
        userIdentityNumber ?: "",
        type,
        content,
        createdAt,
        MessageStatus.DELIVERED.name,
        mediaStatus,
        null,
        mediaName,
        mediaMimeType,
        mediaSize,
        thumbUrl,
        mediaWidth,
        mediaHeight,
        thumbImage,
        mediaUrl,
        mediaDuration,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        assetType,
        null,
        null,
        null,
        assetUrl,
        assetHeight,
        assetWidth,
        null,
        null,
        null,
        appId,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        sharedUserIsVerified,
        sharedUserAppId,
        sharedMembership,
        mediaWaveform,
        quoteId,
        quoteContent,
        null,
        mentions = null,
        null,
    )
}

fun ChatHistoryMessageItem.absolutePath(context: Context = MixinApplication.appContext): String? {
    if (transcriptId == null && conversationId != null) {
        return when {
            mediaUrl == null -> null
            mediaUrl.isFileUri() -> mediaUrl
            isImage() -> File(context.getImagePath().generateConversationPath(conversationId), mediaUrl).toUri().toString()
            isVideo() -> File(context.getVideoPath().generateConversationPath(conversationId), mediaUrl).toUri().toString()
            isAudio() -> File(context.getAudioPath().generateConversationPath(conversationId), mediaUrl).toUri().toString()
            isData() -> File(context.getDocumentPath().generateConversationPath(conversationId), mediaUrl).toUri().toString()
            isTranscript() -> File(context.getTranscriptDirPath(), mediaUrl).toUri().toString()
            else -> null
        }
    }
    val url = mediaUrl
    return when {
        url == null || mediaUrl == null -> null
        isLive() -> url
        mediaUrl.isFileUri() && File(mediaUrl).exists() -> url
        else -> File(context.getTranscriptDirPath(), url).toUri().toString()
    }
}
