package com.plagueid.app

import android.app.DatePickerDialog
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
import java.text.SimpleDateFormat
import java.util.*

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        binding.birthDateInput.isFocusable = false
        binding.birthDateInput.setOnClickListener { showDatePicker() }

        binding.registerButton.setOnClickListener { doRegister() }
        binding.loginLink.setOnClickListener { finish() }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(
            this,
            { _, y, m, d ->
                binding.birthDateInput.setText(String.format("%02d/%02d/%04d", d, m + 1, y))
            },
            year, month, day
        ).show()
    }

    private fun parseBirthDate(input: String): String? {
        if (input.isEmpty()) return null

        val isoFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        isoFormatter.isLenient = false

        val formats = listOf("dd/MM/yyyy", "yyyy-MM-dd", "dd-MM-yyyy")
        for (format in formats) {
            try {
                val df = SimpleDateFormat(format, Locale.getDefault())
                df.isLenient = false
                val date = df.parse(input) ?: continue
                return isoFormatter.format(date)
            } catch (e: Exception) {
                // probar el siguiente formato
            }
        }
        return null
    }

    private fun doRegister() {
        val firstName = binding.firstNameInput.text.toString().trim()
        val lastName = binding.lastNameInput.text.toString().trim()
        val email = binding.emailInput.text.toString().trim()
        val password = binding.passwordInput.text.toString().trim()
        val birthDateRaw = binding.birthDateInput.text.toString().trim()
        val birthDate = parseBirthDate(birthDateRaw)

        if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Completa los campos obligatorios", Toast.LENGTH_SHORT).show()
            return
        }

        if (birthDateRaw.isNotEmpty() && birthDate == null) {
            Toast.makeText(this, "La fecha debe ser DD/MM/YYYY", Toast.LENGTH_SHORT).show()
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
                    birth_date = birthDate
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
