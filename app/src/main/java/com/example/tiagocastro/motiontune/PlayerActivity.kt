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
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.google.android.material.floatingactionbutton.FloatingActionButton

class PlayerActivity : AppCompatActivity(), SensorEventListener {

    // Variáveis de Sistema e Sensores
    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null
    private var accelSensor: Sensor? = null

    // Player e Lógica
    private var player: ExoPlayer? = null
    private var isReadyToChange = true
    private var playlist: ArrayList<Music> = arrayListOf()
    private var isRepeatOne = false

    // UI Components
    private lateinit var btnPlay: FloatingActionButton
    private lateinit var seekBar: SeekBar
    private lateinit var musicTitle: TextView
    private lateinit var albumArt: ImageView
    private lateinit var albumCard: View
    private lateinit var btnRepeat: ImageButton
    private lateinit var btnShuffle: ImageButton

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        // 1. Inicializar as Views
        initializeViews()

        // 2. Receber dados da MainActivity
        val rawList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("LIST", ArrayList::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("LIST")
        } as? ArrayList<*>

        playlist = rawList?.filterIsInstance<Music>() as? ArrayList<Music> ?: arrayListOf()
        val startIndex = intent.getIntExtra("INDEX", 0)

        // 3. Configurar Player e Sensores
        setupPlayer(startIndex)
        setupControlButtons()
        setupSeekBarLogic()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    private fun initializeViews() {
        btnPlay = findViewById(R.id.btnPlay)
        btnNext = findViewById(R.id.btnNext) // Garante que tens ImageButton btnNext no XML
        btnPrev = findViewById(R.id.btnPrev) // Garante que tens ImageButton btnPrev no XML
        btnRepeat = findViewById(R.id.btnRepeat)
        btnShuffle = findViewById(R.id.btnShuffle)
        seekBar = findViewById(R.id.seekBar)
        musicTitle = findViewById(R.id.musicTitle)
        albumArt = findViewById(R.id.albumArt)
        albumCard = findViewById(R.id.albumCard)
    }

    // Variáveis auxiliares para os botões que faltavam acima
    private lateinit var btnNext: ImageButton
    private lateinit var btnPrev: ImageButton

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
                btnPlay.setImageResource(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
                if (isPlaying) startProgressUpdate() else stopProgressUpdate()
            }
        })
        updateUI()
    }

    private fun setupControlButtons() {
        btnPlay.setOnClickListener {
            animateButtonClick(it)
            if (player?.isPlaying == true) player?.pause() else player?.play()
        }

        btnNext.setOnClickListener { animateButtonClick(it); changeTrack(true) }
        btnPrev.setOnClickListener { animateButtonClick(it); changeTrack(false) }

        btnRepeat.setOnClickListener {
            animateButtonClick(it)
            isRepeatOne = !isRepeatOne
            player?.repeatMode = if (isRepeatOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            btnRepeat.setColorFilter(if (isRepeatOne) ContextCompat.getColor(this, R.color.accent_purple) else ContextCompat.getColor(this, R.color.text_secondary))
        }

        btnShuffle.setOnClickListener {
            animateButtonClick(it)
            player?.shuffleModeEnabled = !(player?.shuffleModeEnabled ?: false)
            btnShuffle.setColorFilter(if (player?.shuffleModeEnabled == true) ContextCompat.getColor(this, R.color.accent_purple) else ContextCompat.getColor(this, R.color.text_secondary))
        }
    }

    private fun setupSeekBarLogic() {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) player?.seekTo(p.toLong())
            }
            override fun onStartTrackingTouch(s: SeekBar?) { stopProgressUpdate() }
            override fun onStopTrackingTouch(s: SeekBar?) { startProgressUpdate() }
        })
    }

    private val updateProgressAction = object : Runnable {
        override fun run() {
            player?.let {
                seekBar.max = it.duration.toInt()
                seekBar.progress = it.currentPosition.toInt()
            }
            handler.postDelayed(this, 1000)
        }
    }

    private fun startProgressUpdate() = handler.post(updateProgressAction)
    private fun stopProgressUpdate() = handler.removeCallbacks(updateProgressAction)

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

    private fun animateButtonClick(view: View) {
        val anim = ScaleAnimation(1f, 0.85f, 1f, 0.85f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)
        anim.duration = 150
        anim.repeatCount = 1
        anim.repeatMode = Animation.REVERSE
        view.startAnimation(anim)
    }

    private fun changeTrack(next: Boolean) {
        isReadyToChange = false
        if (next && player?.hasNextMediaItem() == true) player?.seekToNext()
        else if (!next && player?.hasPreviousMediaItem() == true) player?.seekToPrevious()

        val anim = if (next) android.R.anim.slide_in_left else android.R.anim.slide_out_right
        albumCard.startAnimation(AnimationUtils.loadAnimation(this, anim))
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val matrix = FloatArray(9); SensorManager.getRotationMatrixFromVector(matrix, event.values)
            val orient = FloatArray(3); SensorManager.getOrientation(matrix, orient)
            val roll = Math.toDegrees(orient[2].toDouble()).toFloat()
            if (isReadyToChange) {
                if (roll > 45f) changeTrack(true) else if (roll < -45f) changeTrack(false)
            } else if (Math.abs(roll) < 10f) isReadyToChange = true
        }
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val acc = Math.sqrt((event.values[0]*event.values[0] + event.values[1]*event.values[1] + event.values[2]*event.values[2]).toDouble()) - SensorManager.GRAVITY_EARTH
            if (acc > 18) {
                player?.seekToDefaultPosition((0 until (player?.mediaItemCount ?: 1)).random())
                albumCard.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake_anim))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        rotationSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        accelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        stopProgressUpdate()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        handler.removeCallbacksAndMessages(null)
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
}