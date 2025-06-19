package com.android.lbe.ui.util

import android.widget.Toast
import androidx.annotation.StringRes
import com.android.lbe.sysApp

fun makeToast(@StringRes resId: Int) {
    Toast.makeText(sysApp, resId, Toast.LENGTH_SHORT).show()
}
