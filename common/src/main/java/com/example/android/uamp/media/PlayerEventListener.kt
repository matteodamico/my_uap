package com.example.android.uamp.media

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.Player


class PlayerEventListener(applicationContext: Context) : Player.EventListener {
 private val context= applicationContext

override fun onPlayerError(error: ExoPlaybackException) {
    when (error.type) {
        ExoPlaybackException.TYPE_SOURCE -> {
            Toast.makeText(context,
                R.string.error_media_not_found,
                Toast.LENGTH_LONG).show()
                Log.e(TAG, "TYPE_SOURCE: " + error.sourceException.message)}
        ExoPlaybackException.TYPE_RENDERER -> Log.e(TAG, "TYPE_RENDERER: " + error.rendererException.message)
        ExoPlaybackException.TYPE_UNEXPECTED -> Log.e(TAG, "TYPE_UNEXPECTED: " + error.unexpectedException.message)
    }
}

}
private const val TAG = "Playereventlistner"