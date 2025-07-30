package net.burningtnt.terracotta

import android.app.Activity
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import net.burningtnt.terracotta.RoomManager.startGuestVpnService
import net.burningtnt.terracotta.databinding.ActivityMainBinding
import net.burningtnt.terracotta.service.pendingVpnGuestConfig

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val guestVpnLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingVpnGuestConfig?.let {
                startGuestVpnService(this, it.networkName, it.secret, it.port, it.forwardPort)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        PermissionHelper.requestAllWithExplanationIfNeeded(this) {
            setupUI()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        PermissionHelper.handlePermissionResult(
            requestCode, grantResults,
            onSuccess = {
                if (PermissionHelper.shouldRequestIgnoreBattery(this)) {
                    PermissionHelper.requestIgnoreBattery(this)
                }
                setupUI()
            },
            onFail = {
                MaterialAlertDialogBuilder(this)
                    .setTitle("权限被拒绝")
                    .setMessage("Terracotta 无法运行，必须授予所需权限。")
                    .setPositiveButton("退出") { _, _ -> finish() }
                    .show()
            }
        )
    }

    private fun setupUI() {
        binding.host.setOnClickListener {
            RoomManager.startHosting(this) { code ->
                Toast.makeText(this, code, Toast.LENGTH_SHORT).show()
            }
        }
        binding.guest.setOnClickListener {
            RoomManager.joinRoom(this, guestVpnLauncher, "DXETZ-3B14T-0NVZY-TWK39-FFU3M") { error ->
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
            }
        }
    }
}