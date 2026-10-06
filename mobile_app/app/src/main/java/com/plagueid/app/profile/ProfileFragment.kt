package com.plagueid.app.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.plagueid.app.LegalActivity
import com.plagueid.app.LoginActivity
import com.plagueid.app.R
import com.plagueid.app.api.ApiClient
import com.plagueid.app.api.SessionManager
import com.plagueid.app.api.UpdateProfileRequest
import com.plagueid.app.api.UserProfile
import com.plagueid.app.util.BirthDateWheels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileFragment : Fragment() {

    private lateinit var sessionManager: SessionManager
    private lateinit var nameText: TextView
    private lateinit var emailText: TextView
    private lateinit var ageText: TextView
    private var currentProfile: UserProfile? = null

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

        view.findViewById<View>(R.id.editProfileCard).setOnClickListener { showEditDialog() }
        view.findViewById<View>(R.id.termsCard).setOnClickListener {
            openLegal(LegalActivity.SECTION_TERMS)
        }
        view.findViewById<View>(R.id.privacyCard).setOnClickListener {
            openLegal(LegalActivity.SECTION_PRIVACY)
        }
        view.findViewById<View>(R.id.securityCard).setOnClickListener {
            openLegal(LegalActivity.SECTION_SECURITY)
        }
        view.findViewById<View>(R.id.logoutCard).setOnClickListener { logout() }

        loadCachedProfile()
        loadRemoteProfile()
    }

    private fun openLegal(section: String) {
        startActivity(
            Intent(requireContext(), LegalActivity::class.java)
                .putExtra(LegalActivity.EXTRA_SECTION, section)
        )
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
                        currentProfile = profile
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

    private fun showEditDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_profile, null)
        val firstNameInput = dialogView.findViewById<TextInputEditText>(R.id.editFirstName)
        val lastNameInput = dialogView.findViewById<TextInputEditText>(R.id.editLastName)
        val wheels = BirthDateWheels(dialogView.findViewById(R.id.editBirthDateWheels))

        currentProfile?.let { profile ->
            firstNameInput.setText(profile.firstName)
            lastNameInput.setText(profile.lastName)
            profile.birthDate?.split("-")?.let { parts ->
                runCatching {
                    if (parts.size == 3) {
                        wheels.setDate(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
                    }
                }
            }
        } ?: run {
            sessionManager.getUserName()?.split(" ", limit = 2)?.let { parts ->
                firstNameInput.setText(parts[0])
                if (parts.size > 1) lastNameInput.setText(parts[1])
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.edit_profile)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                saveProfile(
                    firstNameInput.text.toString().trim(),
                    lastNameInput.text.toString().trim(),
                    wheels.toIsoDate()
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun saveProfile(firstName: String, lastName: String, birthDate: String) {
        if (firstName.isEmpty() || lastName.isEmpty()) {
            Toast.makeText(requireContext(), "Completa nombre y apellido", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = UpdateProfileRequest(
                    firstName = firstName,
                    lastName = lastName,
                    birthDate = birthDate
                )
                val response = ApiClient.getApi(requireContext()).updateMe(request)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body() != null) {
                        val profile = response.body()!!
                        currentProfile = profile
                        nameText.text = "${profile.firstName} ${profile.lastName}"
                        ageText.text = getString(R.string.age_format, profile.age.toString())
                        sessionManager.saveUser(profile)
                        Toast.makeText(requireContext(), R.string.profile_updated, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "No se pudo actualizar", Toast.LENGTH_SHORT).show()
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
