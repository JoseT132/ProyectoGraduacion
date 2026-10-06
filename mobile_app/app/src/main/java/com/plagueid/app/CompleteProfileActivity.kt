package com.plagueid.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.plagueid.app.api.ApiClient
import com.plagueid.app.api.SessionManager
import com.plagueid.app.api.UpdateProfileRequest
import com.plagueid.app.databinding.ActivityCompleteProfileBinding
import com.plagueid.app.util.BirthDateWheels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Paso obligatorio tras OAuth (o cualquier cuenta sin fecha de nacimiento):
 * sin birth_date la cuenta no puede usarse. Botón atrás = abortar y volver
 * al login con la sesión limpiada.
 */
class CompleteProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCompleteProfileBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var birthDateWheels: BirthDateWheels

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCompleteProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)
        birthDateWheels = BirthDateWheels(binding.birthDateWheels.root)

        binding.continueButton.setOnClickListener { saveBirthDate() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                sessionManager.clearSession()
                finish()
            }
        })
    }

    private fun saveBirthDate() {
        binding.progressBar.visibility = View.VISIBLE
        binding.continueButton.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = UpdateProfileRequest(birthDate = birthDateWheels.toIsoDate())
                val response = ApiClient.getApi(this@CompleteProfileActivity).updateMe(request)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body() != null) {
                        sessionManager.saveUser(response.body()!!)
                        goToMain()
                    } else {
                        binding.progressBar.visibility = View.GONE
                        binding.continueButton.isEnabled = true
                        Toast.makeText(
                            this@CompleteProfileActivity,
                            "No se pudo guardar la fecha",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.continueButton.isEnabled = true
                    Toast.makeText(this@CompleteProfileActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun goToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
