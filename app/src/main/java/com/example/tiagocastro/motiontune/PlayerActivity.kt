package com.example.tiagocastro.motiontune

import android.content.Context
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.*
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.ScaleAnimation
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.google.android.material.floatingactionbutton.FloatingActionButton

class PlayerActivity : AppCompatActivity(), SensorEventListener {

    // Gerenciamento de Sensores
    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null
    private var accelSensor: Sensor? = null

    // Player e Dados
    private var player: ExoPlayer? = null
    private var isReadyToChange = true
    private var playlist: ArrayList<Music> = arrayListOf()

    // Componentes da Interface
    private lateinit var btnPlay: FloatingActionButton
    private lateinit var seekBar: SeekBar
    private lateinit var musicTitle: TextView
    private lateinit var albumArt: ImageView
    private lateinit var albumCard: View

    // Controle da Barra de Progresso
    private val handler = Handler(Looper.getMainLooper())
    private val updateSeekBarTask = object : Runnable {
        override fun run() {
            player?.let {
                if (it.isPlaying) {
                    seekBar.max = it.duration.toInt()
                    seekBar.progress = it.currentPosition.toInt()
                }
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        // Inicializar Views
        btnPlay = findViewById(R.id.btnPlay)
        seekBar = findViewById(R.id.seekBar)
        musicTitle = findViewById(R.id.musicTitle)
        albumArt = findViewById(R.id.albumArt)
        albumCard = findViewById(R.id.albumCard)

        // Receber Playlist com suporte a diferentes versões do Android
        val rawList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("LIST", ArrayList::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("LIST")
        } as? ArrayList<*>

        playlist = rawList?.filterIsInstance<Music>() as? ArrayList<Music> ?: arrayListOf()
        val index = intent.getIntExtra("INDEX", 0)

        setupPlayer(index)
        setupButtons()
        setupSeekBarListener()

        // Configurar Sensores
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    private fun setupPlayer(index: Int) {
        player = ExoPlayer.Builder(this).build()
        playlist.forEach { music ->
            player?.addMediaItem(MediaItem.fromUri(Uri.parse(music.uriString)))
        }

        player?.prepare()
        player?.seekTo(index, 0L)
        player?.play()

        player?.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateUI()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // Atualiza o ícone do FAB profissional
                btnPlay.setImageResource(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
                if (isPlaying) handler.post(updateSeekBarTask) else handler.removeCallbacks(updateSeekBarTask)
            }
        })
        updateUI()
    }

    private fun updateUI() {
        val currentIdx = player?.currentMediaItemIndex ?: 0
        if (currentIdx < playlist.size) {
            val music = playlist[currentIdx]
            musicTitle.text = music.title
            loadAlbumArt(music.uriString)
        }
    }

    private fun loadAlbumArt(uri: String) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(this, Uri.parse(uri))
            val art = retriever.embeddedPicture
            if (art != null) {
                val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
                albumArt.setImageBitmap(bitmap)
            } else {
                albumArt.setImageResource(android.R.drawable.ic_menu_gallery)
            }
            retriever.release()
        } catch (e: Exception) {
            albumArt.setImageResource(android.R.drawable.ic_menu_gallery)
        }
    }

    private fun setupButtons() {
        btnPlay.setOnClickListener {
            animateClick(it)
            if (player?.isPlaying == true) player?.pause() else player?.play()
        }

        findViewById<ImageButton>(R.id.btnNext).setOnClickListener {
            animateClick(it)
            changeTrack(true)
        }

        findViewById<ImageButton>(R.id.btnPrev).setOnClickListener {
            animateClick(it)
            changeTrack(false)
        }
    }

    private fun setupSeekBarListener() {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) player?.seekTo(p.toLong())
            }
            override fun onStartTrackingTouch(s: SeekBar?) {
                handler.removeCallbacks(updateSeekBarTask)
            }
            override fun onStopTrackingTouch(s: SeekBar?) {
                handler.post(updateSeekBarTask)
            }
        })
    }

    private fun animateClick(v: View) {
        val anim = ScaleAnimation(1f, 0.85f, 1f, 0.85f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)
        anim.duration = 150
        anim.repeatCount = 1
        anim.repeatMode = Animation.REVERSE
        v.startAnimation(anim)
    }

    private fun changeTrack(next: Boolean) {
        isReadyToChange = false
        if (next && player?.hasNextMediaItem() == true) {
            player?.seekToNext()
        } else if (!next && player?.hasPreviousMediaItem() == true) {
            player?.seekToPrevious()
        }

        // Animação de transição lateral na capa do álbum
        val animRes = if (next) android.R.anim.slide_in_left else android.R.anim.slide_out_right
        albumCard.startAnimation(AnimationUtils.loadAnimation(this, animRes))
    }

    override fun onSensorChanged(event: SensorEvent) {
        // Lógica de Inclinação (Roll) para trocar de música
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val matrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(matrix, event.values)
            val orient = FloatArray(3)
            SensorManager.getOrientation(matrix, orient)
            val roll = Math.toDegrees(orient[2].toDouble()).toFloat()

            if (isReadyToChange) {
                if (roll > 45f) changeTrack(true)
                else if (roll < -45f) changeTrack(false)
            } else if (Math.abs(roll) < 10f) {
                isReadyToChange = true
            }
        }

        // Lógica de Shake (Acelerómetro) para Shuffle
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val acc = Math.sqrt((x*x + y*y + z*z).toDouble()) - SensorManager.GRAVITY_EARTH

            if (acc > 18) { // Limiar de força para o abanão
                val randomIdx = (0 until (player?.mediaItemCount ?: 1)).random()
                player?.seekToDefaultPosition(randomIdx)
                albumCard.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake_anim))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        rotationSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        accelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        if (player?.isPlaying == true) handler.post(updateSeekBarTask)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        handler.removeCallbacks(updateSeekBarTask)
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        handler.removeCallbacksAndMessages(null)
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
}