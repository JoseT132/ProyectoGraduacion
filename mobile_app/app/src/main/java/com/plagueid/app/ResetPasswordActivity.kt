package com.plagueid.app

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.plagueid.app.api.ApiClient
import com.plagueid.app.api.ForgotPasswordRequest
import com.plagueid.app.api.ResetPasswordRequest
import com.plagueid.app.databinding.ActivityResetPasswordBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ResetPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResetPasswordBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResetPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.sendCodeButton.setOnClickListener { sendCode() }
        binding.resetButton.setOnClickListener { doReset() }
        binding.backToLogin.setOnClickListener { finish() }
    }

    private fun sendCode() {
        val email = binding.emailInput.text.toString().trim()
        if (email.isEmpty()) {
            Toast.makeText(this, "Ingresa tu correo", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = ApiClient.getApi(this@ResetPasswordActivity)
                    .forgotPassword(ForgotPasswordRequest(email))
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    val body = response.body()
                    if (response.isSuccessful && body != null) {
                        if (body.devCode != null) {
                            Toast.makeText(
                                this@ResetPasswordActivity,
                                getString(R.string.code_sent_dev, body.devCode),
                                Toast.LENGTH_LONG
                            ).show()
                            binding.codeInput.setText(body.devCode)
                        } else {
                            Toast.makeText(
                                this@ResetPasswordActivity,
                                R.string.code_sent,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        Toast.makeText(
                            this@ResetPasswordActivity,
                            "Error: ${response.code()}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@ResetPasswordActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun doReset() {
        val email = binding.emailInput.text.toString().trim()
        val code = binding.codeInput.text.toString().trim()
        val newPassword = binding.newPasswordInput.text.toString().trim()

        if (email.isEmpty() || code.isEmpty() || newPassword.isEmpty()) {
            Toast.makeText(this, R.string.reset_fields_error, Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = ApiClient.getApi(this@ResetPasswordActivity)
                    .resetPassword(ResetPasswordRequest(email, code, newPassword))
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    if (response.isSuccessful) {
                        Toast.makeText(this@ResetPasswordActivity, R.string.reset_done, Toast.LENGTH_LONG).show()
                        finish()
                    } else {
                        val error = response.errorBody()?.string() ?: "Código inválido o expirado"
                        Toast.makeText(this@ResetPasswordActivity, error, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@ResetPasswordActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
