package com.plagueid.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.plagueid.app.api.ApiClient
import com.plagueid.app.api.LoginRequest
import com.plagueid.app.api.OAuthRequest
import com.plagueid.app.api.SessionManager
import com.plagueid.app.databinding.ActivityLoginBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var sessionManager: SessionManager

    private val googleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(Exception::class.java)
                account?.idToken?.let { sendGoogleToken(it) }
            } catch (e: Exception) {
                Toast.makeText(this, "Error de Google: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        if (sessionManager.isLoggedIn()) {
            goToMain()
            return
        }

        binding.loginButton.setOnClickListener { doLogin() }
        binding.registerLink.setOnClickListener { startActivity(Intent(this, RegisterActivity::class.java)) }
        binding.googleButton.setOnClickListener { doGoogleSignIn() }
        binding.facebookButton.setOnClickListener { doFacebookSignIn() }
    }

    private fun doLogin() {
        val email = binding.emailInput.text.toString().trim()
        val password = binding.passwordInput.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Ingresa correo y contraseña", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = ApiClient.getApi(this@LoginActivity).login(LoginRequest(email, password))
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    if (response.isSuccessful && response.body() != null) {
                        sessionManager.saveToken(response.body()!!.accessToken)
                        fetchProfileAndGo()
                    } else {
                        Toast.makeText(this@LoginActivity, "Credenciales inválidas", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@LoginActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun doGoogleSignIn() {
        val webClientId = getString(R.string.google_web_client_id)
        if (webClientId.isBlank()) {
            Toast.makeText(this, "Google OAuth no está configurado", Toast.LENGTH_SHORT).show()
            return
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        val client = GoogleSignIn.getClient(this, gso)
        googleLauncher.launch(client.signInIntent)
    }

    private fun doFacebookSignIn() {
        val appId = getString(R.string.facebook_app_id)
        if (appId.isBlank()) {
            Toast.makeText(this, "Facebook OAuth no está configurado", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "Facebook no está implementado aún. Configura el SDK con tu App ID.", Toast.LENGTH_LONG).show()
    }

    private fun sendGoogleToken(idToken: String) {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = ApiClient.getApi(this@LoginActivity).loginGoogle(OAuthRequest(idToken))
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    if (response.isSuccessful && response.body() != null) {
                        sessionManager.saveToken(response.body()!!.accessToken)
                        fetchProfileAndGo()
                    } else {
                        Toast.makeText(this@LoginActivity, "Error con Google: ${response.message()}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@LoginActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun fetchProfileAndGo() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = ApiClient.getApi(this@LoginActivity).getMe()
                if (response.isSuccessful && response.body() != null) {
                    sessionManager.saveUser(response.body()!!)
                }
                withContext(Dispatchers.Main) { goToMain() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { goToMain() }
            }
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
