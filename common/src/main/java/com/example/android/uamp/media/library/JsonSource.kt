/*
 * Copyright 2017 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.uamp.media.library
import android.annotation.SuppressLint
import android.content.Context
import android.icu.text.SimpleDateFormat
import android.net.Uri
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat.STATUS_NOT_DOWNLOADED
import android.support.v4.media.MediaMetadataCompat
import android.text.format.DateUtils
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.android.uamp.media.R
import com.example.android.uamp.media.extensions.*
import com.example.android.uamp.media.library.JsonSource.JsonMusic
import com.example.android.uamp.media.library.forecast5.Forecast
import com.example.android.uamp.media.library.forecast5.Forecasts
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList


/**
 * Source of [MediaMetadataCompat] objects created from a basic JSON stream.
 *
 * The definition of the JSON is specified in the docs of [JsonMusic] in this file,
 * which is the object representation of it.
 */
class JsonSource(context: Context, private val source: Uri) : AbstractMusicSource() {

    private var catalog: List<MediaMetadataCompat> = emptyList()
    private val glide: RequestManager
    private val context1= context

    init {
        state = STATE_INITIALIZING
        glide = Glide.with(context)
    }

    override fun iterator(): Iterator<MediaMetadataCompat> = catalog.iterator()

    override suspend fun load() {
        updateCatalog(source)?.let { updatedCatalog ->
            catalog = updatedCatalog
            state = STATE_INITIALIZED
        } ?: run {
            catalog = emptyList()
            state = STATE_ERROR
        }
    }

    /**
     * Function to connect to a remote URI and download/process the JSON file that corresponds to
     * [MediaMetadataCompat] objects.
     */
    private suspend fun updateCatalog(catalogUri: Uri): List<MediaMetadataCompat>? {
        return withContext(Dispatchers.IO) {
            val musicCat = try {
                downloadJson(catalogUri)
            } catch (ioException: IOException) {
                return@withContext null
            }

            // Get the base URI to fix up relative references later.
            val baseUri = catalogUri.toString().removeSuffix(catalogUri.lastPathSegment ?: "")
            var resId:Int=0
            musicCat.music.map { song ->
                // The JSON may have paths that are relative to the source of the JSON
                // itself. We need to fix them up here to turn them into absolute paths.
                catalogUri.scheme?.let { scheme ->
                    if (!song.source.startsWith(scheme)) {
                        song.source = baseUri + song.source
                    }
                    if (!song.image.startsWith(scheme)) {
  //                      song.image = baseUri + song.image // file.png is not working
                         resId= context1.resources.getIdentifier(song.image,"drawable",context1.packageName)
                    }
                }
                var artFile:File
                if(resId==0) {
                    // Block on downloading artwork.
                     artFile= glide.applyDefaultRequestOptions(glideOptions)
                            .downloadOnly()
                            .load(song.image)
                            .submit(NOTIFICATION_LARGE_ICON_SIZE, NOTIFICATION_LARGE_ICON_SIZE)
                            .get()
                }
                else {
                    // Block on downloading artwork.
                     artFile = glide.applyDefaultRequestOptions(glideOptions)
                            .downloadOnly()
                            .load(resId)
                            .submit(NOTIFICATION_LARGE_ICON_SIZE, NOTIFICATION_LARGE_ICON_SIZE)
                            .get()
                }
                // Expose file via Local URI
                val artUri = artFile.asAlbumArtContentUri()

                MediaMetadataCompat.Builder()
                        .from(song)
                        .apply {
                            displayIconUri = artUri.toString() // Used by ExoPlayer and Notification
                            albumArtUri = artUri.toString()
                        }
                        .build()
            }.toList()
        }
    }


    /**
     * Attempts to download a catalog from a given Uri.
     *
     * @param catalogUri URI to attempt to download the catalog form.
     * @return The catalog downloaded, or an empty catalog if an error occurred.
     */
    @Throws(IOException::class)
    private fun downloadJson(catalogUri: Uri): JsonCatalog {
        val catalogConn = URL(catalogUri.toString())
        val reader = BufferedReader(InputStreamReader(catalogConn.openStream()))
        /*val content = StringBuilder()
        try {
            var line = reader.readLine()
            while (line != null) {
                content.append(line)
                line = reader.readLine()
            }
        } finally {
            reader.close()
        }*/
        if (catalogUri.toString().contains("google"))
            return Gson().fromJson<JsonCatalog>(reader, JsonCatalog::class.java)
        else {
            val forecast: Forecast = Gson().fromJson<Forecast>(reader, Forecast::class.java)
            return transformToJsonCatalog(forecast)
        }

    }

    @SuppressLint("NewApi")
    private fun transformToJsonCatalog(forecast:Forecast): JsonCatalog {

        val json= JsonCatalog()
        val musics = mutableListOf<JsonMusic>()
        var song:JsonMusic
        var fore:Forecasts
        var zoneId = ZoneId.of("Europe/Rome");
        var i:Long=0
        val iterator = forecast.list.iterator()
        while (iterator.hasNext()) {
            fore = iterator.next()
            song = JsonMusic()
            i++


            var inst = Instant.ofEpochSecond(fore.dt.toLong())
            var dayMonth=""+inst.atZone(zoneId).dayOfMonth
            var month =""+inst.atZone(zoneId).monthValue
            if(month.length==1){
                month= "0$month"
            }
            if(dayMonth.length==1){
                dayMonth= "0$dayMonth"
            }
            song.artist=""+inst.atZone(zoneId).hour +":00"
            song.title= fore.weather[0].description.substring(0,1).toUpperCase()+fore.weather[0].description.substring(1)
            song.id=""+fore.dt+"-"+forecast.city.id//
            song.source= METEO_SOURCE_BASE_URL+inst.atZone(zoneId).year+"-"+month+"-"+dayMonth+PODCAST_FILE_EXTENSION  //https://media.ilmeteo.it/audio/2020-03-23.mp3
            song.image=WEATHERCONDITION_FOLDER+fore.weather[0].icon
            song.duration=30
            song.totalTrackCount=8
            song.genre="podcast"
            song.trackNumber=i
            var dayAndNumber= inst.atZone(zoneId).dayOfWeek.getDisplayName(TextStyle.FULL,Locale.ITALIAN)+" "+dayMonth
            song.album=dayAndNumber+", " +forecast.city.name
            musics.add(song)

        }

        json.music=musics
        return json
    }

    /**
     * Extension method for [MediaMetadataCompat.Builder] to set the fields from
     * our JSON constructed object (to make the code a bit easier to see).
     */
    fun MediaMetadataCompat.Builder.from(jsonMusic: JsonMusic): MediaMetadataCompat.Builder {
        // The duration from the JSON is given in seconds, but the rest of the code works in
        // milliseconds. Here's where we convert to the proper units.
        val durationMs = TimeUnit.SECONDS.toMillis(jsonMusic.duration)

        id = jsonMusic.id
        title = jsonMusic.title
        artist = jsonMusic.artist
        album = jsonMusic.album
        duration = durationMs
        genre = jsonMusic.genre
        mediaUri = jsonMusic.source
        albumArtUri = jsonMusic.image
        trackNumber = jsonMusic.trackNumber
        trackCount = jsonMusic.totalTrackCount
        flag = MediaItem.FLAG_PLAYABLE

        // To make things easier for *displaying* these, set the display properties as well.
        displayTitle = jsonMusic.title
        displaySubtitle = jsonMusic.artist
        displayDescription = jsonMusic.album
        displayIconUri = jsonMusic.image


        // Add downloadStatus to force the creation of an "extras" bundle in the resulting
        // MediaMetadataCompat object. This is needed to send accurate metadata to the
        // media session during updates.
        downloadStatus = STATUS_NOT_DOWNLOADED

        // Allow it to be used in the typical builder style.
        return this
    }

    /**
     * Wrapper object for our JSON in order to be processed easily by GSON.
     */
    class JsonCatalog {
        var music: List<JsonMusic> = ArrayList()
    }

    /**
     * An individual piece of music included in our JSON catalog.
     * The format from the server is as specified:
     * ```
     *     { "music" : [
     *     { "title" : // Title of the piece of music
     *     "album" : // Album title of the piece of music
     *     "artist" : // Artist of the piece of music
     *     "genre" : // Primary genre of the music
     *     "source" : // Path to the music, which may be relative
     *     "image" : // Path to the art for the music, which may be relative
     *     "trackNumber" : // Track number
     *     "totalTrackCount" : // Track count
     *     "duration" : // Duration of the music in seconds
     *     "site" : // Source of the music, if applicable
     *     }
     *     ]}
     * ```
     *
     * `source` and `image` can be provided in either relative or
     * absolute paths. For example:
     * ``
     *     "source" : "https://www.example.com/music/ode_to_joy.mp3",
     *     "image" : "ode_to_joy.jpg"
     * ``
     *
     * The `source` specifies the full URI to download the piece of music from, but
     * `image` will be fetched relative to the path of the JSON file itself. This means
     * that if the JSON was at "https://www.example.com/json/music.json" then the image would be found
     * at "https://www.example.com/json/ode_to_joy.jpg".
     */
    @Suppress("unused")
    class JsonMusic {
        var id: String = ""
        var title: String = ""
        var album: String = ""
        var artist: String = ""
        var genre: String = ""
        var source: String = ""
        var image: String = ""
        var trackNumber: Long = 0
        var totalTrackCount: Long = 0
        var duration: Long = -1
        var site: String = ""
    }
}

private const val NOTIFICATION_LARGE_ICON_SIZE = 144 // px

private val glideOptions = RequestOptions()
        .fallback(R.drawable.wc_02d)
        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)

private const val OPENWEATHER_BASE_IMAGE_URL = "http://openweathermap.org/img/wn/"
private const val WEATHERCONDITION_FOLDER = "wc_"
private const val METEO_SOURCE_BASE_URL= "https://media.ilmeteo.it/audio/"
private const val PODCAST_FILE_EXTENSION = ".mp3"


