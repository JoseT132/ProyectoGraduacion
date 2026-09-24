package com.plagueid.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.plagueid.app.api.SessionManager
import com.plagueid.app.databinding.ActivityMainBinding
import com.plagueid.app.home.HomeFragment
import com.plagueid.app.history.HistoryFragment
import com.plagueid.app.profile.ProfileFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var currentTabIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val session = SessionManager(this)
        if (!session.isLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, HomeFragment())
                .commit()
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val (fragment, index) = when (item.itemId) {
                R.id.nav_home -> HomeFragment() to 0
                R.id.nav_history -> HistoryFragment() to 1
                R.id.nav_profile -> ProfileFragment() to 2
                else -> HomeFragment() to 0
            }
            if (index == currentTabIndex) return@setOnItemSelectedListener true

            val transaction = supportFragmentManager.beginTransaction()
            if (index > currentTabIndex) {
                transaction.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
            } else {
                transaction.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right)
            }
            transaction.replace(R.id.fragmentContainer, fragment).commit()
            currentTabIndex = index
            true
        }
    }
}
