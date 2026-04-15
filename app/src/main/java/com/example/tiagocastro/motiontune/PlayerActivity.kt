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

class PlayerActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null
    private var accelSensor: Sensor? = null
    private var player: ExoPlayer? = null
    private var isReadyToChange = true
    private var playlist: ArrayList<Music> = arrayListOf()

    private lateinit var btnPlay: ImageButton
    private lateinit var seekBar: SeekBar
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        btnPlay = findViewById(R.id.btnPlay)
        seekBar = findViewById(R.id.seekBar)

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

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    private fun setupPlayer(index: Int) {
        player = ExoPlayer.Builder(this).build()
        playlist.forEach { player?.addMediaItem(MediaItem.fromUri(Uri.parse(it.uriString))) }
        player?.prepare()
        player?.seekTo(index, 0L)
        player?.play()

        player?.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { updateUI() }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                btnPlay.setImageResource(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
                if (isPlaying) startSeekBarUpdate() else stopSeekBarUpdate()
            }
        })
        updateUI()
    }

    private fun setupSeekBarListener() {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) player?.seekTo(p.toLong())
            }
            override fun onStartTrackingTouch(s: SeekBar?) { stopSeekBarUpdate() }
            override fun onStopTrackingTouch(s: SeekBar?) { startSeekBarUpdate() }
        })
    }

    private fun startSeekBarUpdate() {
        handler.post(object : Runnable {
            override fun run() {
                player?.let {
                    seekBar.max = it.duration.toInt()
                    seekBar.progress = it.currentPosition.toInt()
                }
                handler.postDelayed(this, 1000)
            }
        })
    }

    private fun stopSeekBarUpdate() { handler.removeCallbacksAndMessages(null) }

    private fun updateUI() {
        val currentIdx = player?.currentMediaItemIndex ?: 0
        if (currentIdx < playlist.size) {
            val music = playlist[currentIdx]
            findViewById<TextView>(R.id.musicTitle).text = music.title
            loadAlbumArt(music.uriString)
        }
    }

    private fun loadAlbumArt(uri: String) {
        val img = findViewById<ImageView>(R.id.albumArt)
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(this, Uri.parse(uri))
            val art = retriever.embeddedPicture
            if (art != null) img.setImageBitmap(BitmapFactory.decodeByteArray(art, 0, art.size))
            else img.setImageResource(android.R.drawable.ic_menu_gallery)
            retriever.release()
        } catch (e: Exception) { img.setImageResource(android.R.drawable.ic_menu_gallery) }
    }

    private fun animateClick(v: View) {
        val anim = ScaleAnimation(1f, 0.85f, 1f, 0.85f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)
        anim.duration = 150
        anim.repeatCount = 1
        anim.repeatMode = Animation.REVERSE
        v.startAnimation(anim)
    }

    private fun setupButtons() {
        btnPlay.setOnClickListener { animateClick(it); if (player?.isPlaying == true) player?.pause() else player?.play() }
        findViewById<ImageButton>(R.id.btnNext).setOnClickListener { animateClick(it); changeTrack(true) }
        findViewById<ImageButton>(R.id.btnPrev).setOnClickListener { animateClick(it); changeTrack(false) }
    }

    private fun changeTrack(next: Boolean) {
        isReadyToChange = false
        if (next && player?.hasNextMediaItem() == true) player?.seekToNext()
        else if (!next && player?.hasPreviousMediaItem() == true) player?.seekToPrevious()

        val anim = if (next) android.R.anim.slide_in_left else android.R.anim.slide_out_right
        findViewById<View>(R.id.albumCard).startAnimation(AnimationUtils.loadAnimation(this, anim))
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
                findViewById<View>(R.id.albumCard).startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake_anim))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        rotationSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        accelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onPause() { super.onPause(); sensorManager.unregisterListener(this); stopSeekBarUpdate() }
    override fun onDestroy() { super.onDestroy(); player?.release() }
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
}