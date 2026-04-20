package com.faaa97.obdusbtotcpbridge

import android.content.*
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var logView: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        logView = this.findViewById(R.id.logs_container)
        lifecycleScope.launch {
            ObdLogBus.logs.collect { msg ->
                logView.addView(TextView(logView.context).apply { text = msg })
            }
        }

        this.findViewById<Button>(R.id.start_button).setOnClickListener {
            startService(Intent(this@MainActivity, ObdService::class.java))
        }

        this.findViewById<Button>(R.id.stop_button).setOnClickListener {
            stopService(Intent(this@MainActivity, ObdService::class.java))
        }

    }

}