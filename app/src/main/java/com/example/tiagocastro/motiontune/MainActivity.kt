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

    // Listas para gestão de dados
    private var musicList = ArrayList<Music>()
    private var filteredList = ArrayList<Music>()

    // Componentes de UI
    private lateinit var recyclerView: RecyclerView
    private lateinit var btnSort: Button // Variável global para ser vista por todas as funções

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializar RecyclerView
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Verificar permissões e carregar músicas
        checkPermission()

        // Configurar botões do topo
        setupTopButtons()
    }

    private fun setupTopButtons() {
        val btnSearch = findViewById<ImageButton>(R.id.btnSearchTrigger)
        val searchInput = findViewById<EditText>(R.id.searchEditText)
        val titleLibrary = findViewById<TextView>(R.id.tvLibraryTitle)
        val btnPlayAll = findViewById<ImageButton>(R.id.btnPlayAll)
        val btnShuffleAll = findViewById<View>(R.id.btnShuffleAll)

        btnSort = findViewById(R.id.btnSort)
        btnSort.text = "A-Z" // Texto inicial

        // 1. Lógica da Lupa (Abrir/Fechar Pesquisa)
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
                filterList("") // Resetar a lista ao fechar
            }
        }

        // 2. Pesquisa em Tempo Real
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterList(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // 3. Ordenação A-Z / Z-A (Funciona ao 1º clique)
        btnSort.setOnClickListener {
            if (btnSort.text == "A-Z") {
                // Utilizador quer ordenar A -> Z
                filteredList.sortBy { it.title.lowercase() }
                btnSort.text = "Z-A"
            } else {
                // Utilizador quer ordenar Z -> A
                filteredList.sortByDescending { it.title.lowercase() }
                btnSort.text = "A-Z"
            }
            // Atualizar o adaptador imediatamente
            recyclerView.adapter = MusicAdapter(filteredList) { _, pos ->
                startPlayer(filteredList, pos)
            }
        }

        // 4. Botão Play All (Ícone Play à direita)
        btnPlayAll.setOnClickListener {
            if (filteredList.isNotEmpty()) {
                startPlayer(filteredList, 0)
            }
        }

        // 5. Botão Shuffle (Texto à esquerda)
        btnShuffleAll.setOnClickListener {
            if (filteredList.isNotEmpty()) {
                val randomPos = (0 until filteredList.size).random()
                startPlayer(filteredList, randomPos)
            }
        }
    }

    private fun filterList(query: String) {
        val lowerQuery = query.lowercase()

        // Filtrar a partir da lista original de músicas carregadas
        filteredList = if (query.isEmpty()) {
            ArrayList(musicList)
        } else {
            ArrayList(musicList.filter {
                it.title.lowercase().contains(lowerQuery) ||
                        it.artist.lowercase().contains(lowerQuery)
            })
        }

        // Aplicar a ordenação que está ativa no momento da pesquisa
        if (btnSort.text == "Z-A") {
            filteredList.sortBy { it.title.lowercase() }
        } else {
            filteredList.sortByDescending { it.title.lowercase() }
        }

        // Atualizar o RecyclerView com os resultados filtrados
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
        } else {
            loadMusic()
        }
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

        // Sincronizar as listas e ordenar inicialmente A-Z
        filteredList = ArrayList(musicList)
        filteredList.sortBy { it.title.lowercase() }

        recyclerView.adapter = MusicAdapter(filteredList) { _, pos ->
            startPlayer(filteredList, pos)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadMusic()
        } else {
            Toast.makeText(this, "Permissão negada", Toast.LENGTH_SHORT).show()
        }
    }
}