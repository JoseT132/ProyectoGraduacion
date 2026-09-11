package com.plagueid.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.plagueid.app.api.ApiClient
import com.plagueid.app.api.RegisterRequest
import com.plagueid.app.api.SessionManager
import com.plagueid.app.databinding.ActivityRegisterBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        binding.registerButton.setOnClickListener { doRegister() }
        binding.loginLink.setOnClickListener { finish() }
    }

    private fun doRegister() {
        val firstName = binding.firstNameInput.text.toString().trim()
        val lastName = binding.lastNameInput.text.toString().trim()
        val email = binding.emailInput.text.toString().trim()
        val password = binding.passwordInput.text.toString().trim()
        val birthDate = binding.birthDateInput.text.toString().trim()

        if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Completa los campos obligatorios", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = RegisterRequest(
                    first_name = firstName,
                    last_name = lastName,
                    email = email,
                    password = password,
                    birth_date = birthDate.ifEmpty { null }
                )
                val response = ApiClient.getApi(this@RegisterActivity).register(request)
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    if (response.isSuccessful && response.body() != null) {
                        sessionManager.saveToken(response.body()!!.accessToken)
                        fetchProfileAndGo()
                    } else {
                        val error = response.errorBody()?.string() ?: "Error al registrar"
                        Toast.makeText(this@RegisterActivity, error, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@RegisterActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun fetchProfileAndGo() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = ApiClient.getApi(this@RegisterActivity).getMe()
                if (response.isSuccessful && response.body() != null) {
                    sessionManager.saveUser(response.body()!!)
                }
                withContext(Dispatchers.Main) {
                    startActivity(Intent(this@RegisterActivity, MainActivity::class.java))
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    startActivity(Intent(this@RegisterActivity, MainActivity::class.java))
                    finish()
                }
            }
        }
    }
}
