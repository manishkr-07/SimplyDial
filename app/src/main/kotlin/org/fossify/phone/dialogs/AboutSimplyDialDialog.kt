package org.fossify.phone.dialogs

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.widget.Toast
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.phone.BuildConfig
import org.fossify.phone.R
import org.fossify.phone.databinding.DialogAboutSimplydialBinding

class AboutSimplyDialDialog(val activity: Activity) {
    init {
        val binding = DialogAboutSimplydialBinding.inflate(LayoutInflater.from(activity))

        binding.aboutAppVersion.text = "Version ${BuildConfig.VERSION_NAME}"

        binding.aboutWebsiteBtn.setOnClickListener {
            openUrl(activity.getString(R.string.about_website_url))
        }

        binding.aboutGithubBtn.setOnClickListener {
            openUrl(activity.getString(R.string.about_github_url))
        }

        binding.aboutDiscordBtn.setOnClickListener {
            openUrl(activity.getString(R.string.about_discord_url))
        }

        binding.aboutRedditBtn.setOnClickListener {
            openUrl(activity.getString(R.string.about_reddit_url))
        }

        binding.aboutTelegramBtn.setOnClickListener {
            openUrl(activity.getString(R.string.about_telegram_url))
        }

        binding.aboutEmailBtn.setOnClickListener {
            sendEmail(activity.getString(R.string.about_support_email))
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok) { dialog, _ -> dialog.dismiss() }
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.about_simplydial)
            }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            activity.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(activity, "Could not open link: $url", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendEmail(email: String) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$email")
                putExtra(Intent.EXTRA_SUBJECT, "SimplyDial Support & Feedback")
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            openUrl("mailto:$email")
        }
    }
}
