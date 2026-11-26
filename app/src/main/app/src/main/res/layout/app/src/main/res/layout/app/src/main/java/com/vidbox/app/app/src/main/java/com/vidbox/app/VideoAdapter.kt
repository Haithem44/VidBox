package com.vidbox.app

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.vidbox.app.databinding.ItemVideoBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class VideoAdapter(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onClick: (VideoItem) -> Unit
) : ListAdapter<VideoItem, VideoAdapter.VH>(Diff) {

    private val thumbCache = LruCache<String, Bitmap>(60)
    private val durationCache = HashMap<String, String>()

    class VH(val b: ItemVideoBinding) : RecyclerView.ViewHolder(b.root) {
        var job: Job? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.job?.cancel()
        holder.b.title.text = item.name
        holder.b.root.setOnClickListener { onClick(item) }

        val key = item.uri.toString()
        val cachedThumb = thumbCache.get(key)
        val cachedDur = durationCache[key]
        if (cachedThumb != null && cachedDur != null) {
            holder.b.thumb.setImageBitmap(cachedThumb)
            holder.b.duration.text = cachedDur
            holder.b.duration.visibility = View.VISIBLE
            return
        }

        holder.b.thumb.setImageBitmap(null)
        holder.b.duration.visibility = View.GONE
        holder.job = scope.launch {
            val meta = withContext(Dispatchers.IO) { loadMeta(item) }
            val bmp = meta.first
            val dur = meta.second
            if (bmp != null) {
                thumbCache.put(key, bmp)
                holder.b.thumb.setImageBitmap(bmp)
            }
            if (dur != null) {
                durationCache[key] = dur
                holder.b.duration.text = dur
                holder.b.duration.visibility = View.VISIBLE
            }
        }
    }

    override fun onViewRecycled(holder: VH) {
        holder.job?.cancel()
        super.onViewRecycled(holder)
    }

    private fun loadMeta(item: VideoItem): Pair<Bitmap?, String?> {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, item.uri)
            val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val frame = r.getFrameAtTime(
                (ms ?: 0L).coerceAtMost(3000L) * 1000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )
            val thumb = frame?.let { Bitmap.createScaledBitmap(it, 240, 136, true) }
            Pair(thumb, ms?.let { format(it) })
        } catch (e: Exception) {
            Pair(null, null)
        } finally {
            try {
                r.release()
            } catch (e: Exception) {
            }
        }
    }

    private fun format(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }

    object Diff : DiffUtil.ItemCallback<VideoItem>() {
        override fun areItemsTheSame(a: VideoItem, b: VideoItem) = a.uri == b.uri
        override fun areContentsTheSame(a: VideoItem, b: VideoItem) = a == b
    }
}
