package com.softbankrobotics.app.activities

import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.design.activity.RobotActivity
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks  // Add this line
import com.aldebaran.qi.sdk.design.activity.conversationstatus.SpeechBarDisplayStrategy
import com.softbankrobotics.app.R
import com.softbankrobotics.app.ui.TutorialToolbar
import com.softbankrobotics.app.util.TUTORIAL_NAME_KEY
import com.softbankrobotics.dx.pepperextras.util.SingleThread
import com.softbankrobotics.dx.pepperextras.util.TAG
import com.softbankrobotics.dx.pepperextras.util.asyncFuture
import com.softbankrobotics.dx.pepperextras.util.awaitOrNull
import com.softbankrobotics.pepperaruco.util.OpenCVUtils
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

abstract class BaseActivity : RobotActivity(), RobotLifecycleCallbacks {

    protected abstract val layoutId: Int
    public val appScope = SingleThread.GlobalScope

    private var serverSocket: ServerSocket? = null
    private var future: Future<Unit>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSpeechBarDisplayStrategy(SpeechBarDisplayStrategy.IMMERSIVE)
        setContentView(layoutId)
        setupToolbar()
        OpenCVUtils.loadOpenCV(this)
        QiSDK.register(this, this)

        // Start TCP server in a separate thread
        startTcpServer()
    }

    override fun onDestroy() {
        super.onDestroy()
        QiSDK.unregister(this)

        // Stop TCP server
        stopTcpServer()
    }

    fun setupToolbar() {
        val toolbar: TutorialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setOnClickListener { onBackPressed() }
        val nameNotFound = -1
        val nameResId = intent.getIntExtra(TUTORIAL_NAME_KEY, nameNotFound)
        if (nameResId != nameNotFound) {
            toolbar.setName(nameResId)
        }
        findViewById<ImageView>(R.id.close_button).setOnClickListener { finishAffinity() }
    }

    abstract suspend fun onRobotFocus(qiContext: QiContext)

    override fun onRobotFocusGained(qiContext: QiContext) {
        Log.d(TAG, "Focus gained")
        future = appScope.asyncFuture {
            try {
                onRobotFocus(qiContext)
            } catch (e: Throwable) {
                Log.e(TAG, "Uncaught error: $e")
            }
            Unit
        }
    }

    override fun onRobotFocusLost() {
        Log.d(TAG, "Focus lost")
        runBlocking {
            future?.apply { requestCancellation() }?.awaitOrNull()
            future = null
        }
    }

    private fun startTcpServer() {
        appScope.asyncFuture {
            try {
                // Start a TCP server on a separate thread

                Log.d(TAG, "Starting server...")

                serverSocket = ServerSocket(12345) // Specify your desired port number

                Log.d(TAG, "Created socket: ${serverSocket}")

                Log.d(TAG, "Waiting for client to connect...")

                while (true) {
                    val clientSocket: Socket = serverSocket!!.accept()
                    Log.d(TAG, "Client connected.")
                    handleClient(clientSocket)
                }


            } catch (e: Exception) {
                Log.e(TAG, "Error in TCP server: ${e.message}")
            }
        }
    }

    private fun stopTcpServer() {
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing server socket: ${e.message}")
        }
    }

    private fun handleClient(clientSocket: Socket) {
        appScope.asyncFuture {
            try {
                val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
                val writer = PrintWriter(clientSocket.getOutputStream(), true)

                while (true) {
                    val message = reader.readLine()
                    if (message == null) break

                    // Log the received message
                    Log.d(TAG, "Server received: $message")

                    // Send back the formatted message
                    writer.println("Server received: $message")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error handling client: ${e.message}")
            } finally {
                clientSocket.close()
            }
        }
    }
}
