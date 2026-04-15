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
import android.view.animation.AnimationUtils
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class PlayerActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null
    private var accelSensor: Sensor? = null
    private var player: ExoPlayer? = null
    private var isReadyToChange = true
    private var playlist: ArrayList<Music> = arrayListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        // Receber a lista via Serializable
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
        })
        updateUI() // Atualiza o primeiro item
    }

    private fun updateUI() {
        val currentIdx = player?.currentMediaItemIndex ?: 0
        if (currentIdx < playlist.size) {
            val music = playlist[currentIdx]
            findViewById<TextView>(R.id.musicTitle).text = music.title
            loadAlbumArt(music.uriString)
        }
    }

    private fun loadAlbumArt(uriString: String) {
        val imageView = findViewById<ImageView>(R.id.albumArt)
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(this, Uri.parse(uriString))
            val art = retriever.embeddedPicture
            if (art != null) {
                val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
                imageView.setImageBitmap(bitmap)
            } else {
                imageView.setImageResource(android.R.drawable.ic_media_play)
            }
            retriever.release()
        } catch (e: Exception) {
            imageView.setImageResource(android.R.drawable.ic_media_play)
        }
    }

    private fun triggerFeedbackAnimation(animType: String) {
        val albumCard = findViewById<androidx.cardview.widget.CardView>(R.id.albumCard)
        val animation = when (animType) {
            "NEXT" -> AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left)
            "PREV" -> AnimationUtils.loadAnimation(this, android.R.anim.slide_out_right)
            "SHAKE" -> AnimationUtils.loadAnimation(this, R.anim.shake_anim)
            else -> null
        }
        animation?.let { albumCard.startAnimation(it) }
    }

    private fun setupButtons() {
        findViewById<ImageButton>(R.id.btnPlay).setOnClickListener {
            if (player?.isPlaying == true) player?.pause() else player?.play()
        }
        findViewById<ImageButton>(R.id.btnNext).setOnClickListener { changeTrack(true) }
        findViewById<ImageButton>(R.id.btnPrev).setOnClickListener { changeTrack(false) }
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
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}