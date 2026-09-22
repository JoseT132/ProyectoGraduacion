package com.plagueid.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.plagueid.app.api.ApiClient
import com.plagueid.app.api.ForgotPasswordRequest
import com.plagueid.app.api.VerifyCodeRequest
import com.plagueid.app.databinding.ActivityVerifyCodeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VerifyCodeActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EMAIL = "extra_email"
        const val EXTRA_DEV_CODE = "extra_dev_code"
    }

    private lateinit var binding: ActivityVerifyCodeBinding
    private var email: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVerifyCodeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        email = intent.getStringExtra(EXTRA_EMAIL) ?: ""
        intent.getStringExtra(EXTRA_DEV_CODE)?.let { binding.codeInput.setText(it) }

        binding.verifyButton.setOnClickListener { verifyCode() }
        binding.resendLink.setOnClickListener { resend() }
    }

    private fun verifyCode() {
        val code = binding.codeInput.text.toString().trim()
        if (code.length < 6) {
            Toast.makeText(this, "Ingresa el código de 6 dígitos", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = ApiClient.getApi(this@VerifyCodeActivity)
                    .verifyResetCode(VerifyCodeRequest(email, code))
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    if (response.isSuccessful) {
                        startActivity(
                            Intent(this@VerifyCodeActivity, NewPasswordActivity::class.java)
                                .putExtra(NewPasswordActivity.EXTRA_EMAIL, email)
                                .putExtra(NewPasswordActivity.EXTRA_CODE, code)
                        )
                    } else {
                        Toast.makeText(
                            this@VerifyCodeActivity,
                            "Código inválido o expirado",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@VerifyCodeActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun resend() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = ApiClient.getApi(this@VerifyCodeActivity)
                    .forgotPassword(ForgotPasswordRequest(email))
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    val body = response.body()
                    if (response.isSuccessful && body != null) {
                        body.devCode?.let { binding.codeInput.setText(it) }
                        Toast.makeText(this@VerifyCodeActivity, R.string.code_sent, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                }
            }
        }
    }
}
