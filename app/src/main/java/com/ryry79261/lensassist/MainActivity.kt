package com.ryry79261.lensassist

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * The only screen. Says whether LensAssist currently holds the assistant role, points at
 * the settings that grant it, and offers a Test button that runs the bundled sample
 * image through exactly the code path the assist gesture uses.
 *
 * The diagnostics block answers the two questions that can only be settled on a real
 * device: what the Google app actually exposes for a shared PNG, and how large a
 * screenshot the system hands out.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var diagResolved: TextView
    private lateinit var diagShot: TextView
    private lateinit var diagRoute: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)
        diagResolved = findViewById(R.id.diag_resolved)
        diagShot = findViewById(R.id.diag_shot)
        diagRoute = findViewById(R.id.diag_route)

        findViewById<Button>(R.id.open_settings).setOnClickListener { openAssistantSettings() }
        findViewById<Button>(R.id.send_test).setOnClickListener { sendTestImage() }
        findViewById<Button>(R.id.refresh).setOnClickListener { refresh() }

        val strategy = findViewById<RadioGroup>(R.id.strategy)
        strategy.check(
            when (Diagnostics.launchStrategy(this)) {
                LaunchStrategy.NEW_TASK -> R.id.strategy_new_task
                LaunchStrategy.ASSISTANT -> R.id.strategy_assistant
            },
        )
        strategy.setOnCheckedChangeListener { _, checked ->
            Diagnostics.setLaunchStrategy(
                this,
                if (checked == R.id.strategy_assistant) LaunchStrategy.ASSISTANT
                else LaunchStrategy.NEW_TASK,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // The role can change while we are in the background — the settings button
        // sends the user somewhere that changes it.
        refresh()
    }

    private fun refresh() {
        status.setText(roleStatus())

        diagResolved.text = when {
            !LensLauncher.googleAppInstalled(this) ->
                getString(R.string.diag_google_app_missing)

            else -> LensLauncher.candidates(this)
                .takeIf { it.isNotEmpty() }
                ?.let { targets ->
                    getString(
                        R.string.diag_resolved,
                        targets.size,
                        targets.joinToString("\n") { "  ${it.name}" },
                    )
                }
                ?: getString(R.string.diag_resolved_none)
        }

        diagShot.text = Diagnostics.lastScreenshot(this)
            ?.let { (width, height, config) ->
                getString(R.string.diag_last_shot, width, height, config)
            }
            ?: getString(R.string.diag_last_shot_none)

        diagRoute.text = Diagnostics.lastRoute(this)
            ?.let { getString(R.string.diag_last_route, it) }
            ?: getString(R.string.diag_last_route_none)
    }

    private fun roleStatus(): Int {
        val roles = getSystemService(RoleManager::class.java) ?: return R.string.status_unknown
        if (!roles.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) return R.string.status_unknown
        return if (roles.isRoleHeld(RoleManager.ROLE_ASSISTANT)) R.string.status_held
        else R.string.status_not_held
    }

    /**
     * ROLE_ASSISTANT cannot be requested with `RoleManager.createRequestRoleIntent`, so
     * the best we can do is drop the user on the right settings screen. OEMs move it,
     * hence the chain.
     */
    private fun openAssistantSettings() {
        val candidates = listOf(
            Intent("android.settings.VOICE_INPUT_SETTINGS"),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (intent in candidates) {
            try {
                startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
                // Try the next one.
            }
        }
        Toast.makeText(this, R.string.err_settings, Toast.LENGTH_LONG).show()
    }

    private fun sendTestImage() {
        // Decoded without density scaling: the test card is a fixed-size image, and
        // letting the framework rescale it would change what Lens is asked to read.
        val options = BitmapFactory.Options().apply { inScaled = false }
        val bitmap = resources.openRawResource(R.raw.sample_card).use {
            BitmapFactory.decodeStream(it, null, options)
        }
        if (bitmap == null) {
            Toast.makeText(this, R.string.err_test_image, Toast.LENGTH_LONG).show()
            return
        }
        val result = LensLauncher.send(this, bitmap) { startActivity(it) }
        if (result is LensLauncher.Result.Failed) {
            Toast.makeText(
                this,
                getString(R.string.err_launch_failed, result.detail),
                Toast.LENGTH_LONG,
            ).show()
        }
        refresh()
    }
}
