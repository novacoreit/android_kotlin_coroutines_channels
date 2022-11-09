package it.novacore.hw2_coroutines_kotlin_android

import android.icu.text.SimpleDateFormat
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.toList
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.toList
import java.util.*
import kotlin.system.measureTimeMillis

class MainActivity : AppCompatActivity() {
    private lateinit var clBufferSize: ConstraintLayout
    private lateinit var clProductors: ConstraintLayout
    private lateinit var clConsumers: ConstraintLayout
    private lateinit var editTextBufferSize: EditText
    private lateinit var editTextProductors: EditText
    private lateinit var editTextConsumers: EditText
    private lateinit var buttonExecute: Button
    private lateinit var buttonBufferSizePlus: Button
    private lateinit var buttonProductorsPlus: Button
    private lateinit var buttonConsumersPlus: Button
    private lateinit var buttonConsumersMinus: Button
    private lateinit var buttonProductorsMinus: Button
    private lateinit var buttonBufferSizeMinus: Button
    private lateinit var textActualBufferSize: TextView
    private lateinit var textLog: TextView
    private lateinit var scrollViewLogs: ScrollView
    private var actualBufferSize : Int = 0 // for debug reason

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        loadView()

        buttonExecute.setOnClickListener {
            // launch a lifecycleScope with the executionTask function
            lifecycleScope.launch(Dispatchers.Default) { executeTask() }
        }
    }

    private suspend fun executeTask() {
        updateUI(true)
        val bufferSize = editTextBufferSize.text.toString().toInt()
        val productorsSize = editTextProductors.text.toString().toInt()
        val consumersSize = editTextConsumers.text.toString().toInt()
        val channel = Channel<Int>(bufferSize)

        updateUI(0, bufferSize)
        updateUI("Start from: ${Thread.currentThread().name}")

        val time = measureTimeMillis {
            val mainJob = lifecycleScope.launch(Dispatchers.IO) {
                //var buffer = ArrayList<String>()
                actualBufferSize = 0
                var consumerJobs = ArrayList<Job>()
                var productJobs = ArrayList<Job>()

                var count = 0
                repeat(consumersSize) {
                    count++
                    val job = launch(newSingleThreadContext("ConsumerJob $count")) { consumer(channel, bufferSize) }
                    consumerJobs.add(job)
                }

                count = 0
                repeat(productorsSize) {
                    count++
                    val job = launch(newSingleThreadContext("ProductorJob $count")) { productor(channel, bufferSize) }
                    productJobs.add(job)
                }

                launch { jobsMonitor(channel, bufferSize, productJobs, consumerJobs) }

            }

            // join of the mainJob - waits for all its related jobs to finish
            mainJob.join()
        }

        updateUI("End from: ${Thread.currentThread().name}")
        updateUI("Total Time: $time ms")
        updateUI(false)
    }

    /*
    * Productor job, check the possibility of producing until it is possible
    * */
    private suspend fun productor(channel: Channel<Int>, bufferSize: Int) {
        delay((1 until 10).random() * 1000L)
        channel.send(10)
        actualBufferSize++
        updateUI(actualBufferSize, bufferSize)
        updateUI("Product from thread: ${Thread.currentThread().name}")
    }

    /*
    * Consumer job, verifies the possibility of consuming the buffer, until it is possible
    * */
    private suspend fun consumer(channel: Channel<Int>, bufferSize: Int) {
        delay((1 until 10).random() * 1000L)
        val ree = channel.receive()
        actualBufferSize--
        updateUI(actualBufferSize, bufferSize)
        updateUI("Consumer $ree from thread: ${Thread.currentThread().name}")
    }

    /*
    * UI update functions
    * */
    private suspend fun jobsMonitor(channel: Channel<Int>, bufferSize: Int, productJobs: ArrayList<Job>, consumerJobs: ArrayList<Job>) {
        while (true) {
            delay(500L)
            if (consumerJobs.filter { j -> j.isActive }.isNotEmpty() || productJobs.filter { j -> j.isActive }.isNotEmpty()) {

                // if a consumerJobs is still active but no productJobs and buffer is empty
                if (consumerJobs.filter { j -> j.isActive }.isNotEmpty()
                    && productJobs.filter { j -> j.isCompleted }.count() == productJobs.size
                    && channel.isEmpty
                ) {
                    updateUI("${consumerJobs.filter { j -> j.isActive }.count()} consumerJobs still active but no productsJob is yet!")
                    consumerJobs.filter { j -> j.isActive }.forEach {
                        updateUI("Cancelling... ${it.job}")
                        it.cancel()
                    }
                    return;
                }

                // if no consumerJobs are acctive but products still
                if (productJobs.filter { j -> j.isActive }.isNotEmpty()
                    && consumerJobs.filter { j -> j.isCompleted }.count() == consumerJobs.size
                ) {
                    updateUI("${productJobs.filter { j -> j.isActive }.count()} productJobs still active but no consumerJob active!")
                    productJobs.filter { j -> j.isActive }.forEach {
                        updateUI("Cancelling... ${it.job}")
                        it.cancel()
                    }
                    return;
                }
            } else {
                // if a buffer is not empty but no job is active
                if (!channel.isEmpty)
                    updateUI("The buffer still contains $actualBufferSize")
                return
            }
        }
    }

    /*
    * UI update functions
    * */
    suspend fun updateUI(actual_buffer_size: Int, buffer_size: Int) {
        withContext(Dispatchers.Main) {
            textActualBufferSize.text = String.format("Actual buffer size %d/%d", actual_buffer_size, buffer_size)
        }
    }

    suspend fun updateUI(isRunning: Boolean) {
        withContext(Dispatchers.Main) {
            if (isRunning)
                textLog.text = ""
            buttonExecute.isEnabled = !isRunning
            clBufferSize.isEnabled = !isRunning
            clProductors.isEnabled = !isRunning
            clConsumers.isEnabled = !isRunning
        }
    }

    suspend fun updateUI(logMessage: String) {
        withContext(Dispatchers.Main) {
            val sdf = SimpleDateFormat("HH:mm:ss.SSS")
            textLog.text = textLog.text.toString().plus(String.format("%s: %s\n", sdf.format(Date()), logMessage))
            delay(200L)
            scrollViewLogs.fullScroll(ScrollView.FOCUS_DOWN);
        }
    }

    /*
    * View
    * */
    private fun loadView() {
        buttonExecute = findViewById(R.id.buttonExecute)
        buttonBufferSizePlus = findViewById(R.id.buttonBufferSizePlus)
        buttonProductorsPlus = findViewById(R.id.buttonProductorsPlus)
        buttonConsumersPlus = findViewById(R.id.buttonConsumersPlus)
        buttonConsumersMinus = findViewById(R.id.buttonConsumersMinus)
        buttonProductorsMinus = findViewById(R.id.buttonProductorsMinus)
        buttonBufferSizeMinus = findViewById(R.id.buttonBufferSizeMinus)
        editTextBufferSize = findViewById(R.id.editTextBufferSize)
        editTextProductors = findViewById(R.id.editTextProductors)
        editTextConsumers = findViewById(R.id.editTextConsumers)
        textActualBufferSize = findViewById(R.id.textActualBufferSize)
        textLog = findViewById(R.id.textLog)
        scrollViewLogs = findViewById(R.id.scrollViewLogs)
        clBufferSize = findViewById(R.id.clBufferSize)
        clConsumers = findViewById(R.id.clConsumers)
        clProductors = findViewById(R.id.clProductors)

        val plusMinusListner = View.OnClickListener { view ->
            when (view.getId()) {
                R.id.buttonBufferSizePlus -> {
                    editTextBufferSize.setText((editTextBufferSize.text.toString().toInt() + 1).toString())
                }
                R.id.buttonConsumersPlus -> {
                    editTextConsumers.setText((editTextConsumers.text.toString().toInt() + 1).toString())
                }
                R.id.buttonProductorsPlus -> {
                    editTextProductors.setText((editTextProductors.text.toString().toInt() + 1).toString())
                }
                R.id.buttonBufferSizeMinus -> {
                    if (editTextBufferSize.text.toString().toInt() - 1 > 0)
                        editTextBufferSize.setText((editTextBufferSize.text.toString().toInt() - 1).toString())
                }
                R.id.buttonConsumersMinus -> {
                    if (editTextConsumers.text.toString().toInt() - 1 > 0)
                        editTextConsumers.setText((editTextConsumers.text.toString().toInt() - 1).toString())
                }
                R.id.buttonProductorsMinus -> {
                    if (editTextProductors.text.toString().toInt() - 1 > 0)
                        editTextProductors.setText((editTextProductors.text.toString().toInt() - 1).toString())
                }

            }
        }

        buttonBufferSizePlus.setOnClickListener(plusMinusListner)
        buttonProductorsPlus.setOnClickListener(plusMinusListner)
        buttonConsumersPlus.setOnClickListener(plusMinusListner)
        buttonConsumersMinus.setOnClickListener(plusMinusListner)
        buttonProductorsMinus.setOnClickListener(plusMinusListner)
        buttonBufferSizeMinus.setOnClickListener(plusMinusListner)
    }
}