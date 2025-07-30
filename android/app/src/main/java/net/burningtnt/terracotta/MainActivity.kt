package net.burningtnt.terracotta

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import net.burningtnt.terracotta.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

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
            RoomManager.joinRoom(this, "DXETZ-3B14T-0NVZY-TWK39-FFU3M") { error ->
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
            }
        }
    }
}