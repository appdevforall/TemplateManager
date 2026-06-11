package org.appdevforall.templatemanager.models

import android.net.Uri
import java.io.File

data class CgtFileItem(
    val uri: Uri,
    val name: String,
    var isChecked: Boolean = false
)