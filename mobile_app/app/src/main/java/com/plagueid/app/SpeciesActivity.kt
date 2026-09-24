package com.plagueid.app

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.plagueid.app.api.ApiClient
import com.plagueid.app.databinding.ActivitySpeciesBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SpeciesActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySpeciesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySpeciesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val slug = intent.getStringExtra(EXTRA_SLUG)
        if (slug.isNullOrBlank()) {
            Toast.makeText(this, R.string.ficha_error, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadSpecies(slug)
    }

    private fun loadSpecies(slug: String) {
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.getApi(this@SpeciesActivity).getSpecies(slug)
                }
                val species = response.body()
                if (response.isSuccessful && species != null) {
                    bindSpecies(species)
                } else {
                    showError()
                }
            } catch (e: Exception) {
                showError()
            }
        }
    }

    private fun bindSpecies(species: Species) {
        loadSpeciesImage(species.slug)
        binding.scientificName.text = species.scientificName
        binding.commonName.text = species.commonName ?: ""
        binding.commonName.visibility =
            if (species.commonName.isNullOrBlank()) View.GONE else View.VISIBLE
        binding.familyChip.text = species.family ?: "—"

        binding.descriptionText.text = species.description ?: "—"
        binding.lifeCycleText.text = species.lifeCycle ?: "—"
        binding.hostsText.text = species.hosts ?: "—"
        binding.damageText.text = species.damage ?: "—"

        binding.thresholdText.text = species.threshold ?: "—"
        binding.biologicalText.text = species.biologicalControl ?: "—"
        binding.culturalText.text = species.culturalControl ?: "—"
        binding.chemicalText.text = species.chemicalControl ?: "—"

        binding.distributionText.text =
            species.distribution?.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "—"
        binding.referencesText.text = species.references ?: "—"

        binding.progressBar.visibility = View.GONE
        binding.contentContainer.visibility = View.VISIBLE
    }

    private fun loadSpeciesImage(slug: String) {
        try {
            assets.open("species/$slug.jpg").use { input ->
                binding.speciesImage.setImageBitmap(
                    android.graphics.BitmapFactory.decodeStream(input)
                )
            }
        } catch (e: Exception) {
            binding.speciesImage.setImageResource(R.drawable.ic_leaf_light)
        }
    }

    private fun showError() {
        binding.progressBar.visibility = View.GONE
        Toast.makeText(this, R.string.ficha_error, Toast.LENGTH_SHORT).show()
        finish()
    }

    companion object {
        const val EXTRA_SLUG = "slug"
    }
}
