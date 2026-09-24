package com.plagueid.app

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class LegalActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_legal)

        val section = intent.getStringExtra(EXTRA_SECTION)
        val (titleRes, bodyRes) = when (section) {
            SECTION_PRIVACY -> R.string.privacy_title to R.string.privacy_text
            SECTION_SECURITY -> R.string.security_title to R.string.security_text
            else -> R.string.terms_title to R.string.terms_text
        }

        findViewById<TextView>(R.id.titleText).setText(titleRes)
        findViewById<TextView>(R.id.bodyText).setText(bodyRes)
        findViewById<ImageButton>(R.id.backButton).setOnClickListener { finish() }
    }

    companion object {
        const val EXTRA_SECTION = "section"
        const val SECTION_TERMS = "terms"
        const val SECTION_PRIVACY = "privacy"
        const val SECTION_SECURITY = "security"
    }
}
