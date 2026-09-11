package com.plagueid.app.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.plagueid.app.LoginActivity
import com.plagueid.app.R
import com.plagueid.app.api.ApiClient
import com.plagueid.app.api.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileFragment : Fragment() {

    private lateinit var sessionManager: SessionManager
    private lateinit var nameText: TextView
    private lateinit var emailText: TextView
    private lateinit var ageText: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sessionManager = SessionManager(requireContext())

        nameText = view.findViewById(R.id.nameText)
        emailText = view.findViewById(R.id.emailText)
        ageText = view.findViewById(R.id.ageText)

        view.findViewById<View>(R.id.logoutButton).setOnClickListener { logout() }

        loadCachedProfile()
        loadRemoteProfile()
    }

    private fun loadCachedProfile() {
        nameText.text = sessionManager.getUserName() ?: "—"
        emailText.text = sessionManager.getUserEmail() ?: "—"
        ageText.text = getString(R.string.age_format, "—")
    }

    private fun loadRemoteProfile() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = ApiClient.getApi(requireContext()).getMe()
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body() != null) {
                        val profile = response.body()!!
                        nameText.text = "${profile.firstName} ${profile.lastName}"
                        emailText.text = profile.email
                        ageText.text = getString(R.string.age_format, profile.age.toString())
                        sessionManager.saveUser(profile)
                    } else {
                        Toast.makeText(requireContext(), "No se pudo cargar el perfil", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun logout() {
        sessionManager.clearSession()
        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}
