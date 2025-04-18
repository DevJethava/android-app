package one.mixin.android.ui.media.pager.transcript

import android.content.Context
import android.os.Build
import android.util.LruCache
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import one.mixin.android.R
import one.mixin.android.databinding.ItemPagerVideoLayoutBinding
import one.mixin.android.extension.dp
import one.mixin.android.extension.loadImage
import one.mixin.android.extension.navigationBarHeight
import one.mixin.android.extension.realSize
import one.mixin.android.extension.toBitmap
import one.mixin.android.job.MixinJobManager.Companion.getAttachmentProcess
import one.mixin.android.session.Session
import one.mixin.android.ui.media.pager.MediaPagerActivity.Companion.PREFIX
import one.mixin.android.ui.media.pager.PlayerView
import one.mixin.android.util.VideoPlayer
import one.mixin.android.vo.ChatHistoryMessageItem
import one.mixin.android.vo.MediaStatus
import one.mixin.android.vo.absolutePath
import one.mixin.android.vo.isLive
import one.mixin.android.vo.loadVideoOrLive
import one.mixin.android.widget.CircleProgress

class VideoHolder(
    itemView: View,
    private val mediaPagerAdapterListener: MediaPagerAdapterListener,
) : MediaPagerHolder(itemView) {
    val binding = ItemPagerVideoLayoutBinding.bind(itemView)

    init {
        itemView.post {
            binding.playerView.playerControlView.bottomLayout.setPadding(12.dp, 24.dp, 12.dp, 12.dp + itemView.context.navigationBarHeight())
        }
    }

    fun bind(
        messageItem: ChatHistoryMessageItem,
        needPostTransition: Boolean,
        videoStatusCache: LruCache<String, String>,
    ) {
        val context = itemView.context
        val circleProgress = itemView.findViewById<CircleProgress>(R.id.circle_progress)

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O) {
            binding.playerView.playerControlView.fullscreenIv.isVisible = false
        }

        binding.playerView.playerControlView.closeIv.setOnClickListener {
            mediaPagerAdapterListener.finishAfterTransition()
        }
        binding.playerView.playerControlView.pipView.setOnClickListener {
            binding.playerView.hideController()
            mediaPagerAdapterListener.switchToPin(messageItem, itemView)
        }
        binding.playerView.playerControlView.fullscreenIv.setOnClickListener {
            mediaPagerAdapterListener.switchFullscreen()
        }
        binding.playerView.playerControlView.pipView.isEnabled = false
        binding.playerView.playerControlView.pipView.alpha = 0.5f

        binding.playerView.apply {
            currentMessageId = messageItem.messageId
            setPlaybackPrepare {
                messageItem.loadVideoOrLive { showPb() }
            }
            if (needPostTransition) {
                player = VideoPlayer.player().player
            }
            refreshAction = {
                messageItem.loadVideoOrLive { showPb() }
            }
            callback =
                object : PlayerView.Callback {
                    override fun onLongClick() {
                        mediaPagerAdapterListener.onLongClick(messageItem, itemView)
                    }

                    override fun onRenderFirstFrame() {
                        if (VideoPlayer.player().mId == messageItem.messageId) {
                            binding.playerView.videoAspectRatio.updateLayoutParams {
                                width = MATCH_PARENT
                                height = MATCH_PARENT
                            }
                            binding.previewIv.isVisible = false
                            binding.playerView.playerControlView.pipView.isEnabled = true
                            binding.playerView.playerControlView.pipView.alpha = 1f
                        } else {
                            binding.previewIv.isVisible = true
                            binding.playerView.playerControlView.pipView.isEnabled = false
                            binding.playerView.playerControlView.pipView.alpha = .5f
                        }
                    }
                }
        }
        val ratio = messageItem.mediaWidth!!.toFloat() / messageItem.mediaHeight!!.toFloat()
        setSize(context, ratio)
        itemView.tag = "$PREFIX${messageItem.messageId}"
        if (messageItem.isLive()) {
            circleProgress.isVisible = false
            binding.previewIv.loadImage(data = messageItem.thumbUrl, base64Holder = messageItem.thumbImage)
        } else {
            if (messageItem.mediaUrl != null) {
                binding.previewIv.loadImage(data = messageItem.absolutePath(), base64Holder = null)
            } else {
                val imageData =
                    messageItem.thumbImage?.toBitmap(
                        messageItem.mediaWidth,
                        messageItem.mediaHeight,
                    )
                binding.previewIv.setImageBitmap(imageData)
            }
            if (messageItem.mediaStatus == MediaStatus.DONE.name || messageItem.mediaStatus == MediaStatus.READ.name) {
                maybeLoadVideo(videoStatusCache, messageItem)
                circleProgress.isVisible = false
                circleProgress.setBindId(null)
            } else {
                circleProgress.isVisible = true
                circleProgress.setBindId(messageItem.messageId)
                if (messageItem.mediaStatus == MediaStatus.PENDING.name) {
                    circleProgress.enableLoading(getAttachmentProcess(messageItem.messageId))
                } else if (messageItem.mediaStatus == MediaStatus.CANCELED.name) {
                    if (Session.getAccountId() == messageItem.userId) {
                        circleProgress.enableUpload()
                    } else {
                        circleProgress.enableDownload()
                    }
                } else {
                    // TODO expired
                }
                circleProgress.setOnClickListener {
                    mediaPagerAdapterListener.onCircleProgressClick(messageItem)
                }
            }
        }
        if (needPostTransition) {
            ViewCompat.setTransitionName(itemView, "transition")
            mediaPagerAdapterListener.onReadyPostTransition(itemView)
        }
    }

    private fun maybeLoadVideo(
        videoStatusCache: LruCache<String, String>,
        messageItem: ChatHistoryMessageItem,
    ) {
        val preStatus = videoStatusCache[messageItem.messageId] ?: return
        if (preStatus != MediaStatus.DONE.name && preStatus != MediaStatus.READ.name &&
            (messageItem.mediaStatus == MediaStatus.DONE.name || messageItem.mediaStatus == MediaStatus.READ.name)
        ) {
            messageItem.loadVideoOrLive {
                VideoPlayer.player().start()
            }
        }
    }

    private fun setSize(
        context: Context,
        ratio: Float,
    ) {
        val w = context.realSize().x
        val h = context.realSize().y
        val deviceRatio = w / h.toFloat()
        val ratioParams = binding.playerView.videoAspectRatio.layoutParams
        val previewParams = binding.previewIv.layoutParams
        if (deviceRatio > ratio) {
            ratioParams.height = h
            ratioParams.width = (h * ratio).toInt()
        } else {
            ratioParams.width = w
            ratioParams.height = (w / ratio).toInt()
        }
        previewParams.width = ratioParams.width
        previewParams.height = ratioParams.height
        binding.playerView.videoAspectRatio.layoutParams = ratioParams
        binding.previewIv.layoutParams = previewParams
    }
}
