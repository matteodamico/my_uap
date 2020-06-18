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

package com.example.android.uamp

import android.Manifest
import android.Manifest.permission
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.example.android.uamp.fragments.MediaItemFragment
import com.example.android.uamp.media.MusicService
import com.example.android.uamp.utils.Event
import com.example.android.uamp.utils.InjectorUtils
import com.example.android.uamp.utils.showSnackbar
import com.example.android.uamp.viewmodels.MainActivityViewModel
import com.google.android.material.snackbar.Snackbar


class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: MainActivityViewModel
    private lateinit var layout: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        layout = findViewById(R.id.fragmentContainer)


        // Since this app has a music player, the volume controls should adjust the music volume
        // while in the app.
        volumeControlStream = AudioManager.STREAM_MUSIC

        viewModel = ViewModelProviders
                .of(this, InjectorUtils.provideMainActivityViewModel(this))
                .get(MainActivityViewModel::class.java)

        if (ContextCompat.checkSelfPermission(this,
                        permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermission()
        //} else {
            // Permission has already been granted
        }

        /**
         * Observe [MainActivityViewModel.navigateToFragment] for [Event]s that request a
         * fragment swap.
         */
        viewModel.navigateToFragment.observe(this, Observer {
            it?.getContentIfNotHandled()?.let { fragmentRequest ->
                val transaction = supportFragmentManager.beginTransaction()
                transaction.replace(
                        R.id.fragmentContainer, fragmentRequest.fragment, fragmentRequest.tag)
                if (fragmentRequest.backStack) transaction.addToBackStack(null)
                transaction.commit()
            }
        })

        /**
         * Observe changes to the [MainActivityViewModel.rootMediaId]. When the app starts,
         * and the UI connects to [MusicService], this will be updated and the app will show
         * the initial list of media items.
         */
        viewModel.rootMediaId.observe(this,
                Observer { rootMediaId ->
                    if (rootMediaId != null) {
                        navigateToMediaItem(rootMediaId)
                    }
                })

        /**
         * Observe [MainActivityViewModel.navigateToMediaItem] for [Event]s indicating
         * the user has requested to browse to a different [MediaItemData].
         */
        viewModel.navigateToMediaItem.observe(this, Observer {
            it?.getContentIfNotHandled()?.let { mediaId ->
                navigateToMediaItem(mediaId)
            }
        })
    }

    private fun navigateToMediaItem(mediaId: String) {
        var fragment: MediaItemFragment? = getBrowseFragment(mediaId)
        if (fragment == null) {
            fragment = MediaItemFragment.newInstance(mediaId)
            // If this is not the top level media (root), we add it to the fragment
            // back stack, so that actionbar toggle and Back will work appropriately:
            viewModel.showFragment(fragment, !isRootId(mediaId), mediaId)
        }
    }

    private fun isRootId(mediaId: String) = mediaId == viewModel.rootMediaId.value

    private fun getBrowseFragment(mediaId: String): MediaItemFragment? {
        return supportFragmentManager.findFragmentByTag(mediaId) as MediaItemFragment?
    }

    private fun requestLocationPermission() {
        // Should we show an explanation?
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.ACCESS_COARSE_LOCATION)) {

            layout.showSnackbar(R.string.location_course_access_required,
                    Snackbar.LENGTH_INDEFINITE, R.string.ok) {
                ActivityCompat.requestPermissions(this,
                        arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                        REQUEST_ACCESS_COARSE_LOCATION_CODE)
            }
        } else {
            // No explanation needed, we can request the permission.
            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                    REQUEST_ACCESS_COARSE_LOCATION_CODE)
            // REQUEST_ACCESS_COARSE_LOCATION_CODE is an app-defined init constant
            // The callback method (below) gets the result of the request.
        }
    }

    /**
     * Handle the permissions request response
     */
    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_ACCESS_COARSE_LOCATION_CODE -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    Toast.makeText(applicationContext,
                            R.string.permission_granted,
                            Toast.LENGTH_LONG).show()
                    // permission was granted, yay!
                } else {
                    Toast.makeText(applicationContext,
                            R.string.permission_not_granted,
                            Toast.LENGTH_LONG).show()
                    //TODO:
                    // permission denied, boo! Disable the functionality that depends on this permission.
                }
                return
            }
            // Add other 'when' lines to check for other
            // permissions this app might request.
            else -> {
                // Ignore all other requests.
            }
        }
    }
}

private const val REQUEST_ACCESS_COARSE_LOCATION_CODE = 2