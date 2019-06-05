package com.flxrs.dankchat.utils

import android.content.ActivityNotFoundException
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ImageSpan
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.text.bold
import androidx.core.text.color
import androidx.databinding.BindingAdapter
import com.bumptech.glide.Glide
import com.flxrs.dankchat.R
import com.flxrs.dankchat.chat.ChatItem
import com.flxrs.dankchat.service.twitch.emote.EmoteManager
import com.linkedin.urls.detection.UrlDetector
import com.linkedin.urls.detection.UrlDetectorOptions
import kotlin.math.roundToInt

@BindingAdapter("setTwitchMessage")
fun TextView.setTwitchMessage(item: ChatItem?) = item?.message?.apply {
	text = ""
	movementMethod = LinkMovementMethod.getInstance()
	val lineHeight = this@setTwitchMessage.lineHeight
	val scaleFactor = lineHeight * 1.5 / 112

	val foregroundColor = if (timedOut) ContextCompat.getColor(this@setTwitchMessage.context, R.color.colorTimeOut) else Color.TRANSPARENT
	foreground = ColorDrawable(foregroundColor)

	val backgroundResource = if (isSystem) {
		R.color.sub_background
	} else {
		android.R.color.transparent
	}
	this@setTwitchMessage.setBackgroundColor(ContextCompat.getColor(this@setTwitchMessage.context, backgroundResource))

	val displayName = if (isAction) "$name " else if (name.isBlank()) "" else "$name: "
	val prefixLength = time.length + 1 + displayName.length
	var badgesLength = 0
	val spannable = SpannableStringBuilder().bold { append("$time ") }

	badges.forEach { badge ->
		spannable.append("  ")
		val start = spannable.length - 2
		val end = spannable.length - 1
		badgesLength += 2
		Glide.with(this@setTwitchMessage)
				.asBitmap()
				.load(badge.url)
				.placeholder(R.drawable.ic_missing_emote)
				.error(R.drawable.ic_missing_emote)
				.into(ImageSpanBadgeTarget(context) {
					val width = Math.round(lineHeight * it.intrinsicWidth / it.intrinsicHeight.toFloat())
					it.setBounds(0, 0, width, lineHeight)
					val imageSpan = ImageSpan(it, ImageSpan.ALIGN_BOTTOM)
					spannable.setSpan(imageSpan, start, end, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
					text = spannable
				})
	}

	val normalizedColor = normalizeColor(color)

	spannable.bold { color(normalizedColor) { append(displayName) } }

	if (isAction) {
		spannable.color(normalizedColor) { append(message) }
	} else {
		spannable.append(message)
	}

	//links
	UrlDetector(message, UrlDetectorOptions.Default).detect().forEach { url ->
		val clickableSpan = object : ClickableSpan() {
			override fun onClick(v: View) {
				try {
					androidx.browser.customtabs.CustomTabsIntent.Builder()
							.addDefaultShareMenuItem()
							.setToolbarColor(ContextCompat.getColor(v.context, R.color.colorPrimary))
							.setShowTitle(true)
							.build().launchUrl(v.context, Uri.parse(url.fullUrl))
				} catch (e: ActivityNotFoundException) {
					Log.e("BindingAdapter", Log.getStackTraceString(e))
				}

			}
		}
		val start = prefixLength + badgesLength + message.indexOf(url.originalUrl)
		val end = start + url.originalUrl.length
		spannable.setSpan(clickableSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
		text = spannable
	}

	emotes.forEach { e ->
		e.positions.forEach { pos ->
			val split = pos.split('-')
			val start = prefixLength + badgesLength + split[0].toInt()
			val end = prefixLength + badgesLength + split[1].toInt() + 1
			if (e.isGif) {
				val gifDrawable = EmoteManager.gifCache[e.keyword]
				if (gifDrawable != null) {
					val height = (gifDrawable.intrinsicHeight * scaleFactor).roundToInt()
					val width = (gifDrawable.intrinsicWidth * scaleFactor).roundToInt()
					gifDrawable.setBounds(0, 0, width, height)
					val imageSpan = ImageSpan(gifDrawable, ImageSpan.ALIGN_BOTTOM)
					spannable.setSpan(imageSpan, start, end, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
					text = spannable
				} else {
					Glide.with(this@setTwitchMessage)
							.`as`(ByteArray::class.java)
							.load(e.url)
							.placeholder(R.drawable.ic_missing_emote)
							.error(R.drawable.ic_missing_emote)
							.into(GifDrawableTarget(e.keyword) {
								val height = (it.intrinsicHeight * scaleFactor).roundToInt()
								val width = (it.intrinsicWidth * scaleFactor).roundToInt()
								it.setBounds(0, 0, width, height)
								spannable.setSpan(ImageSpan(it, ImageSpan.ALIGN_BOTTOM), start, end, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
								text = spannable
							})
				}
			} else {
				Glide.with(this@setTwitchMessage)
						.asBitmap()
						.load(e.url)
						.placeholder(R.drawable.ic_missing_emote)
						.error(R.drawable.ic_missing_emote)
						.into(ImageSpanEmoteTarget(e, context) {
							val ratio = it.intrinsicWidth / it.intrinsicHeight.toFloat()

							val height = when {
								it.intrinsicHeight < 55 && e.keyword.isBlank()       -> (70 * scaleFactor).roundToInt()
								it.intrinsicHeight in 55..111 && e.keyword.isBlank() -> (112 * scaleFactor).roundToInt()
								else                                                 -> (it.intrinsicHeight * scaleFactor).roundToInt()
							}
							val width = (height * ratio).roundToInt()
							it.setBounds(0, 0, width, height)
							spannable.setSpan(ImageSpan(it, ImageSpan.ALIGN_BOTTOM), start, end, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
							text = spannable
						})
			}
		}
	}
	text = spannable
}
