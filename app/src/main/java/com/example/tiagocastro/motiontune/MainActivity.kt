package com.example.tiagocastro.motiontune

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private var musicList = ArrayList<Music>()
    private var filteredList = ArrayList<Music>()
    private lateinit var recyclerView: RecyclerView
    private var isAscending = true // Controla se a ordem atual é A-Z ou Z-A

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        checkPermission()
        setupTopButtons()
    }

    private fun setupTopButtons() {
        val btnSearch = findViewById<ImageButton>(R.id.btnSearchTrigger)
        val searchInput = findViewById<EditText>(R.id.searchEditText)
        val titleLibrary = findViewById<TextView>(R.id.tvLibraryTitle)
        val btnSort = findViewById<Button>(R.id.btnSort)
        val btnPlayAll = findViewById<ImageButton>(R.id.btnPlayAll)
        val btnShuffleAll = findViewById<View>(R.id.btnShuffleAll)

        // 1. Lógica da Barra de Pesquisa (Lupa)
        btnSearch.setOnClickListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            if (searchInput.visibility == View.GONE) {
                searchInput.visibility = View.VISIBLE
                titleLibrary.visibility = View.GONE
                btnSearch.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                searchInput.requestFocus()
                imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
            } else {
                searchInput.visibility = View.GONE
                titleLibrary.visibility = View.VISIBLE
                btnSearch.setImageResource(android.R.drawable.ic_menu_search)
                searchInput.text.clear()
                imm.hideSoftInputFromWindow(searchInput.windowToken, 0)
            }
        }

        // 2. Escuta a escrita na pesquisa
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterList(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // 3. Botão de Ordenação (A-Z / Z-A)
        btnSort.setOnClickListener {
            isAscending = !isAscending
            btnSort.text = if (isAscending) "A-Z" else "Z-A"
            applySorting() // Aplica a ordem à lista atual
        }

        // 4. Botões de Shuffle e Play
        btnShuffleAll.setOnClickListener {
            if (filteredList.isNotEmpty()) {
                val randomPos = filteredList.indices.random()
                startPlayer(filteredList, randomPos)
            }
        }

        btnPlayAll.setOnClickListener {
            if (filteredList.isNotEmpty()) {
                startPlayer(filteredList, 0)
            }
        }
    }

    // Função que filtra a lista com base na pesquisa
    private fun filterList(query: String) {
        val lowerQuery = query.lowercase().trim()
        filteredList = if (lowerQuery.isEmpty()) {
            ArrayList(musicList)
        } else {
            ArrayList(musicList.filter {
                it.title.lowercase().contains(lowerQuery) ||
                        it.artist.lowercase().contains(lowerQuery)
            })
        }
        applySorting() // Garante que a lista filtrada mantém a ordem escolhida
    }

    // FUNÇÃO CRUCIAL: Ordena a lista ignorando aspas e símbolos iniciais
    private fun applySorting() {
        if (isAscending) {
            // Ordem A-Z: Remove aspas e espaços apenas para comparar
            filteredList.sortWith(compareBy {
                it.title.replace("\"", "").replace("'", "").lowercase().trim()
            })
        } else {
            // Ordem Z-A
            filteredList.sortWith(compareByDescending {
                it.title.replace("\"", "").replace("'", "").lowercase().trim()
            })
        }

        // Atualiza o adaptador com a lista organizada
        recyclerView.adapter = MusicAdapter(filteredList) { _, pos ->
            startPlayer(filteredList, pos)
        }
    }

    private fun startPlayer(list: ArrayList<Music>, index: Int) {
        val intent = Intent(this, PlayerActivity::class.java)
        intent.putExtra("LIST", list)
        intent.putExtra("INDEX", index)
        startActivity(intent)
    }

    private fun checkPermission() {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(perm), 100)
        } else loadMusic()
    }

    private fun loadMusic() {
        musicList.clear()
        val cursor = contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST),
            "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null)

        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            while (it.moveToNext()) {
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, it.getLong(idCol))
                musicList.add(Music(it.getString(titleCol), it.getString(artistCol), uri.toString()))
            }
        }

        filteredList = ArrayList(musicList)
        applySorting() // Ordenação inicial automática
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) loadMusic()
    }
}