package com.xeno.ui.language

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.xeno.i18n.I18n

class LanguageViewModel(application: Application) : AndroidViewModel(application) {
    fun select(code: String) {
        I18n.setLanguage(getApplication(), code)
    }
}
