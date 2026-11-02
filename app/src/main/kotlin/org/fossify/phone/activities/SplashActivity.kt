package org.fossify.phone.activities

import android.content.Intent
import org.fossify.commons.activities.BaseSplashActivity
import org.fossify.phone.helpers.ContactsRepository

class SplashActivity : BaseSplashActivity() {
    override fun initActivity() {
        ContactsRepository.preload(applicationContext)
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        })
        finish()
    }
}
