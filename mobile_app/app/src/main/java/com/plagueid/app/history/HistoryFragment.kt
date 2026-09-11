package com.plagueid.app.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.plagueid.app.R
import com.plagueid.app.api.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: View
    private lateinit var emptyText: View

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.recyclerView)
        progressBar = view.findViewById(R.id.progressBar)
        emptyText = view.findViewById(R.id.emptyText)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        loadHistory()
    }

    private fun loadHistory() {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = ApiClient.getApi(requireContext()).getDetections()
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    if (response.isSuccessful && response.body() != null) {
                        val items = response.body()!!
                        if (items.isEmpty()) {
                            emptyText.visibility = View.VISIBLE
                        } else {
                            recyclerView.adapter = DetectionAdapter(items)
                        }
                    } else {
                        Toast.makeText(requireContext(), "No se pudo cargar el historial", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
