package com.example.tiagocastro.motiontune

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private val musicList = ArrayList<Music>()
    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        checkPermission()
        setupTopButtons()
    }

    private fun setupTopButtons() {
        findViewById<View>(R.id.btnShuffleAll).setOnClickListener {
            if (musicList.isNotEmpty()) startPlayer(musicList.indices.random())
        }
        findViewById<View>(R.id.btnPlayAll).setOnClickListener {
            if (musicList.isNotEmpty()) startPlayer(0)
        }
    }

    private fun startPlayer(index: Int) {
        val intent = Intent(this, PlayerActivity::class.java)
        intent.putExtra("LIST", musicList)
        intent.putExtra("INDEX", index)
        startActivity(intent)
    }

    private fun checkPermission() {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(perm), 100)
        } else loadMusic()
    }

    private fun loadMusic() {
        musicList.clear()
        val cursor = contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST), "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null)
        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            while (it.moveToNext()) {
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, it.getLong(idCol))
                musicList.add(Music(it.getString(titleCol), it.getString(artistCol), uri.toString()))
            }
        }
        recyclerView.adapter = MusicAdapter(musicList) { _, pos -> startPlayer(pos) }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) loadMusic()
    }
}