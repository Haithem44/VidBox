package com.vidbox.app

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var recycler: RecyclerView
    private lateinit var emptyState: View
    private lateinit var progress: View
    private lateinit var countText: TextView
    private lateinit var searchInput: TextInputEditText
    private lateinit var adapter: VideoAdapter

    private var allVideos: List<VideoItem> = emptyList()
    private var isFullscreen = false
    private var isLocked = false

    private val prefs by lazy { getSharedPreferences("vidbox", MODE_PRIVATE) }

    private val pickFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                prefs.edit().putString("folder", uri.toString()).apply()
                loadFolder(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        playerView = findViewById(R.id.playerView)
        recycler = findViewById(R.id.recycler)
        emptyState = findViewById(R.id.emptyState)
        progress = findViewById(R.id.progress)
        countText = findViewById(R.id.countText)
        searchInput = findViewById(R.id.searchInput)

        player = ExoPlayer.Builder(this).build()
        playerView.player = player
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        })

        adapter = VideoAdapter { playVideo(it) }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        findViewById<MaterialButton>(R.id.changeFolderBtn).setOnClickListener { pickFolder.launch(null) }
        findViewById<MaterialButton>(R.id.emptyPickBtn).setOnClickListener { pickFolder.launch(null) }

        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = filter(s?.toString().orEmpty())
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        setupPlayerButtons()

        prefs.getString("folder", null)?.let { loadFolder(Uri.parse(it)) } ?: showEmpty(true)
    }

    // ---------- أزرار المشغّل: ملء الشاشة + قفل + سرعة ----------
    private fun setupPlayerButtons() {
        val fullscreenBtn = playerView.findViewById<View>(R.id.btn_fullscreen)
        val lockBtn = playerView.findViewById<View>(R.id.btn_lock)
        val speedBtn = playerView.findViewById<TextView>(R.id.btn_speed)
        val unlockBtn = findViewById<View>(R.id.btn_unlock)

        fullscreenBtn.setOnClickListener { toggleFullscreen() }
        lockBtn.setOnClickListener { setLocked(true) }
        unlockBtn.setOnClickListener { setLocked(false) }

        val speeds = floatArrayOf(0.5f, 1f, 1.25f, 1.5f, 2f)
        var idx = 1
        speedBtn.setOnClickListener {
            idx = (idx + 1) % speeds.size
            player.setPlaybackSpeed(speeds[idx])
            speedBtn.text = "${speeds[idx]}x"
        }
    }

    private fun setLocked(locked: Boolean) {
        isLocked = locked
        playerView.useController = !locked
        findViewById<View>(R.id.btn_unlock).visibility = if (locked) View.VISIBLE else View.GONE
        if (locked) playerView.hideController()
    }

    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        val params = playerView.layoutParams
        if (isFullscreen) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            WindowCompat.setDecorFitsSystemWindows(window, false)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            params.height = ViewGroup_MATCH
            findViewById<View>(R.id.listContainer).visibility = View.GONE
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            WindowCompat.setDecorFitsSystemWindows(window, true)
            controller.show(WindowInsetsCompat.Type.systemBars())
            params.height = (220 * resources.displayMetrics.density).toInt()
            findViewById<View>(R.id.listContainer).visibility = View.VISIBLE
        }
        playerView.layoutParams = params
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            isLocked -> Toast.makeText(this, "الشاشة مقفلة", Toast.LENGTH_SHORT).show()
            isFullscreen -> toggleFullscreen()
            else -> super.onBackPressed()
        }
    }

    // ---------- تحميل الفيديوهات ----------
    private fun loadFolder(uri: Uri) {
        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) {
                val dir = DocumentFile.fromTreeUri(this@MainActivity, uri)
                dir?.listFiles()
                    ?.filter { it.isFile && (it.type?.startsWith("video/") == true) }
                    ?.map { VideoItem(it.name ?: "video", it.uri) }
                    ?.sortedBy { it.title.lowercase() }
                    ?: emptyList()
            }
            allVideos = list
            progress.visibility = View.GONE
            filter(searchInput.text?.toString().orEmpty())
        }
    }

    private fun filter(q: String) {
        val result = if (q.isBlank()) allVideos
        else allVideos.filter { it.title.contains(q, ignoreCase = true) }
        adapter.submit(result)
        countText.text = "${result.size} فيديو"
        showEmpty(result.isEmpty())
    }

    private fun showEmpty(empty: Boolean) {
        emptyState.visibility = if (empty) View.VISIBLE else View.GONE
    }

    // ---------- التشغيل ----------
    private fun playVideo(item: VideoItem) {
        val key = item.uri.toString()
        player.setMediaItem(MediaItem.fromUri(item.uri))
        player.prepare()
        player.seekTo(prefs.getLong("pos_$key", 0L))
        player.play()
        playerView.tag = key
    }

    private fun savePosition() {
        val key = playerView.tag as? String ?: return
        prefs.edit().putLong("pos_$key", player.currentPosition).apply()
    }

    override fun onPause() {
        super.onPause()
        savePosition()
        player.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }

    private companion object {
        const val ViewGroup_MATCH = android.view.ViewGroup.LayoutParams.MATCH_PARENT
    }
}
