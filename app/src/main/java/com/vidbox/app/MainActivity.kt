package com.vidbox.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class MainActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var adapter: ArrayAdapter<String>
    private val names = ArrayList<String>()
    private val uris = ArrayList<Uri>()
    private val reqFolder = 101

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL

        val pickBtn = Button(this)
        pickBtn.text = "اختيار المجلد"
        pickBtn.setOnClickListener {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            startActivityForResult(i, reqFolder)
        }
        root.addView(pickBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        playerView = PlayerView(this)
        root.addView(playerView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(220)))

        val listView = ListView(this)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, pos, _ -> play(uris[pos]) }
        root.addView(listView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)

        val saved = getSharedPreferences("vidbox", MODE_PRIVATE).getString("folder", null)
        if (saved != null) {
            try {
                loadFolder(Uri.parse(saved))
            } catch (e: Exception) {
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == reqFolder && resultCode == RESULT_OK && data?.data != null) {
            val uri = data.data!!
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            getSharedPreferences("vidbox", MODE_PRIVATE).edit().putString("folder", uri.toString()).apply()
            loadFolder(uri)
        }
    }

    private fun loadFolder(treeUri: Uri) {
        names.clear()
        uris.clear()
        val treeId = DocumentsContract.getTreeDocumentId(treeUri)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeId)
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        contentResolver.query(children, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                val name = c.getString(1)
                val mime = c.getString(2) ?: ""
                if (mime.startsWith("video/")) {
                    names.add(name)
                    uris.add(DocumentsContract.buildDocumentUriUsingTree(treeUri, id))
                }
            }
        }
        adapter.notifyDataSetChanged()
    }

    private fun play(uri: Uri) {
        player?.release()
        val p = ExoPlayer.Builder(this).build()
        playerView.player = p
        p.setMediaItem(MediaItem.fromUri(uri))
        p.prepare()
        p.play()
        player = p
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}
