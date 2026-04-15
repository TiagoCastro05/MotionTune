package com.example.tiagocastro.motiontune

import android.content.Context
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.ScaleAnimation
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class PlayerActivity : AppCompatActivity(), SensorEventListener {

    // Lógica do Player
    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null
    private var accelSensor: Sensor? = null
    private var player: ExoPlayer? = null
    private var isReadyToChange = true
    private var playlist: ArrayList<Music> = arrayListOf()

    // Elementos da UI
    private lateinit var btnPlay: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var musicTitle: TextView
    private lateinit var albumArt: ImageView
    private lateinit var albumCard: androidx.cardview.widget.CardView

    // Para atualizar o SeekBar
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var updateSeekBar: Runnable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        // Inicializar Views
        btnPlay = findViewById(R.id.btnPlay)
        seekBar = findViewById(R.id.seekBar)
        musicTitle = findViewById(R.id.musicTitle)
        albumArt = findViewById(R.id.albumArt)
        albumCard = findViewById(R.id.albumCard)

        // Receber a lista via Serializable
        val rawList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("LIST", ArrayList::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("LIST")
        } as? ArrayList<*>

        playlist = rawList?.filterIsInstance<Music>() as? ArrayList<Music> ?: arrayListOf()
        val index = intent.getIntExtra("INDEX", 0)

        // Configurar o SeekBar para o utilizador poder arrastar
        setupSeekBarListener()

        // Iniciar Player e Sensores
        setupPlayer(index)
        setupButtons()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    private fun setupPlayer(index: Int) {
        player = ExoPlayer.Builder(this).build()

        // Adicionar músicas à playlist
        playlist.forEach { music ->
            player?.addMediaItem(MediaItem.fromUri(Uri.parse(music.uriString)))
        }

        player?.prepare()
        player?.seekTo(index, 0L)
        player?.play()

        // Listener para mudanças de música e estado (Play/Pause)
        player?.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateUI() // Atualiza título e capa
            }

            // Ponto 2: Detetar quando o player muda entre Play e Pause
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // Atualizar o ícone do botão
                if (isPlaying) {
                    btnPlay.setImageResource(android.R.drawable.ic_media_pause)
                    startSeekBarUpdate() // Começar a mover a barra
                } else {
                    btnPlay.setImageResource(android.R.drawable.ic_media_play)
                    stopSeekBarUpdate() // Parar a barra
                }
            }
        })
        updateUI() // Atualização inicial
    }

    // Ponto 3: Lógica para mover a Barra de Progresso (SeekBar)
    private fun startSeekBarUpdate() {
        seekBar.max = player?.duration?.toInt() ?: 0
        updateSeekBar = Runnable {
            val currentPos = player?.currentPosition?.toInt() ?: 0
            seekBar.progress = currentPos
            handler.postDelayed(updateSeekBar, 1000) // Atualizar a cada segundo
        }
        handler.post(updateSeekBar)
    }

    private fun stopSeekBarUpdate() {
        handler.removeCallbacks(updateSeekBar)
    }

    // Ponto 3.1: Permitir que o utilizador arraste a barra
    private fun setupSeekBarListener() {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    player?.seekTo(progress.toLong()) // Mudar a música para onde o user arrastou
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                stopSeekBarUpdate() // Parar a atualização automática enquanto arrasta
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                startSeekBarUpdate() // Recomeçar a atualização
            }
        })
    }

    // Ponto 1: Animação de Clique nos Botões (Diminuir tamanho)
    private fun animateButtonClick(view: View) {
        // Criar animação de escala (0.9x do tamanho original)
        val anim = ScaleAnimation(
            1f, 0.9f, 1f, 0.9f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        )
        anim.duration = 200 // Duração da animação
        anim.repeatCount = 1 // Ir e Voltar
        anim.repeatMode = Animation.REVERSE // Modo de reverso (voltar ao tamanho normal)
        view.startAnimation(anim)
    }

    private fun setupButtons() {
        findViewById<ImageButton>(R.id.btnPrev).setOnClickListener {
            animateButtonClick(it) // Adicionar animação
            changeTrack(false)
        }

        btnPlay.setOnClickListener {
            animateButtonClick(it) // Adicionar animação
            if (player?.isPlaying == true) {
                player?.pause()
            } else {
                player?.play()
            }
            // O ícone muda automaticamente no Listener (onIsPlayingChanged)
        }

        findViewById<ImageButton>(R.id.btnNext).setOnClickListener {
            animateButtonClick(it) // Adicionar animação
            changeTrack(true)
        }
    }

    private fun updateUI() {
        val currentIdx = player?.currentMediaItemIndex ?: 0
        if (currentIdx < playlist.size) {
            val music = playlist[currentIdx]
            musicTitle.text = music.title
            loadAlbumArt(music.uriString)
        }
    }

    private fun loadAlbumArt(uriString: String) {
        val albumArtImageView = findViewById<ImageView>(R.id.albumArt) // Referência correta
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(this, Uri.parse(uriString))
            val art = retriever.embeddedPicture

            if (art != null) {
                val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
                albumArtImageView.setImageBitmap(bitmap)
            } else {
                // Usa o ícone padrão de galeria aqui também
                albumArtImageView.setImageResource(android.R.drawable.ic_menu_gallery)
            }
            retriever.release()
        } catch (e: Exception) {
            albumArtImageView.setImageResource(android.R.drawable.ic_menu_gallery)
        }
    }

    // Animação de Feedback Visual para Gesto de Sensor (Slide)
    private fun triggerFeedbackAnimation(animType: String) {
        val animation = when (animType) {
            "NEXT" -> AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left)
            "PREV" -> AnimationUtils.loadAnimation(this, android.R.anim.slide_out_right)
            "SHAKE" -> AnimationUtils.loadAnimation(this, R.anim.shake_anim)
            else -> null
        }
        animation?.let { albumCard.startAnimation(it) }
    }

    private fun changeTrack(next: Boolean) {
        isReadyToChange = false
        if (next && player?.hasNextMediaItem() == true) {
            player?.seekToNext()
            triggerFeedbackAnimation("NEXT")
        } else if (!next && player?.hasPreviousMediaItem() == true) {
            player?.seekToPrevious()
            triggerFeedbackAnimation("PREV")
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)
            val rollDegrees = Math.toDegrees(orientation[2].toDouble()).toFloat()

            if (isReadyToChange) {
                if (rollDegrees > 45f) changeTrack(true)
                else if (rollDegrees < -45f) changeTrack(false)
            } else if (Math.abs(rollDegrees) < 10f) {
                isReadyToChange = true
            }
        }

        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
            val acceleration = Math.sqrt((x*x + y*y + z*z).toDouble()) - SensorManager.GRAVITY_EARTH

            // Sensor Shake brusco (valor 18) para evitar conflito
            if (acceleration > 18) {
                val randomIdx = (0 until (player?.mediaItemCount ?: 1)).random()
                player?.seekToDefaultPosition(randomIdx)
                triggerFeedbackAnimation("SHAKE")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        rotationSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        accelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }

        // Retomar a barra se já estiver a tocar
        if (player?.isPlaying == true) {
            startSeekBarUpdate()
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        stopSeekBarUpdate() // Parar a barra ao sair
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        stopSeekBarUpdate()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}