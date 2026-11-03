package com.stanley.foodjournal.photos

import android.content.Context
import android.content.Intent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts

/**
 * The system Photo Picker for one image, asking it to keep the photo's location. The picker
 * asks the user once per app, and the user's choice is final. Apps targeting Android 17 need no
 * permission for this; a picker without the option simply strips the location as before.
 */
class PickPhotoWithLocation : ActivityResultContracts.PickVisualMedia() {
    override fun createIntent(context: Context, input: PickVisualMediaRequest): Intent =
        super.createIntent(context, input).putExtra(EXTRA_REQUEST_LOCATION_METADATA_ACCESS, true)

    private companion object {
        // MediaStore.EXTRA_REQUEST_LOCATION_METADATA_ACCESS. Behind a feature flag, so not yet
        // in the public SDK; the value is the documented one.
        const val EXTRA_REQUEST_LOCATION_METADATA_ACCESS =
            "android.provider.extra.REQUEST_LOCATION_METADATA_ACCESS"
    }
}
