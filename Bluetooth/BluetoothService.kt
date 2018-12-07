
import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import java.util.*
import java.util.concurrent.CountDownLatch

/**
 * Created by janpawlov ( ͡° ͜ʖ ͡°) on 12/07/2018.
 */
@SuppressLint("LogNotTimber")
class BluetoothService : Service() {
    private val SCAN_DURATION: Long = 60000
    private val REFRESH_DELAY: Long = 2000 //refresh characteristic data after this delay
    private val TEST_DELAY: Long = 1000
    private val tag = this.javaClass.simpleName

    private lateinit var mBluetoothManager: BluetoothManager
    private lateinit var mBluetoothAdapter: BluetoothAdapter
    @Volatile
    private var mBluetoothLeScanner: BluetoothLeScanner? = null
    private val mScanSettings = ScanSettings.Builder().build()

    private val mBinder = ServiceBinder()
    private val mBluetoothScanCallback: BluetoothScanCallback = BluetoothScanCallback()
    private val mBluetoothConnectCallback = BluetoothConnectCallback()

    @Volatile
    private var connectedDevices: MutableList<BluetoothGatt> = mutableListOf()

    private val handlerThreadName = "stopScanningHandler"
    private val mHandlerThread: HandlerThread = HandlerThread(handlerThreadName)
    private var mHandler: Handler? = null //handler that stops scanning after set duration
    private val runnable = StopScanRunnable()

    private var testTimer: Timer? = null

    private val bluetoothDeviceDataPublisher: PublishSubject<BluetoothDeviceData> = PublishSubject.create()
    private val resistanceTestPublisher = PublishSubject.create<ResistanceTestData>()
    private var connectedDevicePublisher: PublishSubject<DeviceConnected> = PublishSubject.create()

    private var historicDataPublisher: PublishSubject<HistoricData>? = null

    var serviceDiscoveryCountDownLatch: CountDownLatch? = null
    var startReadingLoopThread: Thread? = null
    var readLoopTimer: Timer? = null

    @Volatile
    private var isReadingActive: Boolean = false

    var numberOfDevices: Int = 0
    lateinit var mTaskExecutor: BluetoothTaskExecutor

    inner class StopScanRunnable : Runnable {
        override fun run() {
            mBluetoothLeScanner?.stopScan(mBluetoothScanCallback)
            sendBroadcast(Intent(ACTION_SCAN_COMPLETE))
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return mBinder
    }

    override fun onCreate() {
        super.onCreate()
        mBluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        mBluetoothAdapter = mBluetoothManager.adapter
        if (!mBluetoothAdapter.isEnabled)
            sendBroadcast(Intent(ACTION_ENABLE_BLUETOOTH_REQUIRED))
        mTaskExecutor = BluetoothTaskExecutor(applicationContext, mBluetoothConnectCallback, connectedDevices)
    }

    override fun onDestroy() {
        for (gatt in connectedDevices)
            mTaskExecutor.disconnectDevice(gatt)
        super.onDestroy()
    }

    private fun startScanning() {
        if (mBluetoothLeScanner == null)
            mBluetoothLeScanner = mBluetoothAdapter.bluetoothLeScanner
        if (!mHandlerThread.isAlive) {
            mHandlerThread.start()
            if (mHandler == null)
                mHandler = Handler(mHandlerThread.looper)
        }
        mHandler?.postDelayed(runnable, SCAN_DURATION)
        Log.d(tag, "Starting Bluetooth Scan")
        mBluetoothLeScanner?.startScan(null, mScanSettings, mBluetoothScanCallback)
    }

    private fun stopScanning() {
        mHandler?.removeCallbacks(runnable)
        mHandler?.post(runnable)
    }

    /**
     * Custom implementation of Bluetooth LE scan callbacks.
     */
    inner class BluetoothScanCallback : ScanCallback() {
        private val tag = this.javaClass.simpleName
        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            when (errorCode) {
                ScanCallback.SCAN_FAILED_ALREADY_STARTED -> Log.e(tag, "ErrorCode: $errorCode; Failed to start scan as BLE scan with the same settings is already started by the app.")
                ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> Log.e(tag, "ErrorCode: $errorCode; Failed to start scan as app cannot be registered.")
                ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> Log.e(tag, "ErrorCode: $errorCode; Failed to start power optimized scan as this feature is not supported.")
                ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> Log.e(tag, "ErrorCode: $errorCode; Failed to start scan due an internal error")
            }
        }

        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            when (callbackType) {
                ScanSettings.CALLBACK_TYPE_FIRST_MATCH,
                ScanSettings.CALLBACK_TYPE_ALL_MATCHES -> handleResult(result)
                ScanSettings.CALLBACK_TYPE_MATCH_LOST -> Log.d(tag, "CALLBACK_TYPE_MATCH_LOST")
            }
        }

        private fun handleResult(result: ScanResult?) {
            val intent = Intent(ACTION_DEVICE_DISCOVERED)
            intent.putExtra(BLUETOOTH_DEVICE, result?.device)
            sendBroadcast(intent)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            super.onBatchScanResults(results)
            Log.d(tag, "onBatchScanResults triggered")
        }
    }

    private fun startReadLoopTimer() {
        if (readLoopTimer == null) {
            readLoopTimer = Timer()
            readLoopTimer?.schedule(object : TimerTask() {
                override fun run() {
                    mTaskExecutor.setSDescriptors(connectedDevices)
                }
            }, TEST_DELAY, REFRESH_DELAY)
            Log.e(tag, "Started reading loop")
        }
    }

    private fun stopReadLoop() {
        readLoopTimer?.cancel()
        readLoopTimer = null
        Log.e(tag, "Cancelled Read loop")
    }

    /**
     * All Bluetooth related callbacks are received here:
     * connection Ses, characteristics discovery, write,
     * read operations
     */
    inner class BluetoothConnectCallback : BluetoothGattCallback() {
        private val tag = this.javaClass.simpleName

        override fun onConnectionStateChange(gatt: BluetoothGatt?, S: Int, newState: Int) {
            super.onConnectionStateChange(gatt, S, newState)
            when (S) {
                BluetoothGatt.GATT_SUCCESS -> {
                    gatt?.let {
                        when (newState) {
                            BluetoothGatt.STATE_CONNECTED -> {
                                Log.d(tag, "Device ${gatt.device.address} connected")
                                if (!connectedDevices.contains(it))
                                    connectedDevices.add(it)
                                mTaskExecutor.discoverServices(gatt)
                            }
                            BluetoothGatt.STATE_DISCONNECTED -> {
                                Log.d(tag, "Device ${gatt.device.address} disconnected")
                                mTaskExecutor.countDownLatch()
                            }
                            else -> {
                            }//Can only be one of the above
                        }
                    }
                }
                else -> {
                    when (S) {
                        8 -> {
                            Log.e(tag, "Device ${gatt?.device?.address} connection S error: timeout, probably out of range")
                            bluetoothDeviceDataPublisher.onNext(BluetoothDeviceData(gatt?.device, DeviceSes.OUT_OF_RANGE, isConnected = true))
                            gatt?.disconnect()
                            if (connectedDevices.contains(gatt))
                                connectedDevices.remove(gatt)
                        }
                        19 -> {
                            Log.e(tag, "Device: ${gatt?.device?.address} has forced a disconnect")
                            reconnect(gatt)
                        }
                        22 -> {
                            Log.e(tag, "Device ${gatt?.device?.address} connection terminated By Local Host")
                            reconnect(gatt)
                        }
                        133 -> {
                            Log.e(tag, "Device ${gatt?.device?.address} connection S error 133")
                            if (!allDevicesConnected)
                                reconnect(gatt)
                        }
                        else -> {
                            Log.e(tag, "Device ${gatt?.device?.address} connection S error $S")
                            reconnect(gatt)
                        }
                    }
                }
            }
        }

        private fun reconnect(gatt: BluetoothGatt?) {
            gatt?.let {
                mTaskExecutor.reattemptConnect(it)
            }
        }

        private var allDevicesConnected = false

        override fun onServicesDiscovered(gatt: BluetoothGatt?, S: Int) {
            super.onServicesDiscovered(gatt, S)
            when (S) {
                BluetoothGatt.GATT_SUCCESS -> {
                    gatt?.let {
                        connectedDevicePublisher.onNext(DeviceConnected(it.device))
                        if (startReadingLoopThread == null) {
                            synchronized(this) {
                                Log.e(this@BluetoothService::class.java.name, "Starting reading loop thread")
                                startReadingLoopThread = Thread {
                                    try {
                                        serviceDiscoveryCountDownLatch?.await()
                                    } catch (e: InterruptedException) {
                                    }
                                    connectedDevicePublisher.onComplete()
                                    allDevicesConnected = true
                                    connectedDevicePublisher = PublishSubject.create()
                                    startReadingLoopThread?.interrupt()
                                    startReadingLoopThread = null
                                }
                                startReadingLoopThread?.start()
                            }
                        }
                        serviceDiscoveryCountDownLatch?.countDown()
                    }
                }
                else -> Log.e(tag, "onServicesDiscovered failed, S code:$S")
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, S: Int) {
            super.onCharacteristicRead(gatt, characteristic, S)
            when (S) {
                BluetoothGatt.GATT_SUCCESS -> {
                    if (characteristic?.uuid == READ_CURATION_UUID)
                        readCurationCharacteristic(characteristic)
                    else if (characteristic?.uuid == READ_S_UUID) {
                        if (readLoopTimer == null) { //this is during test
                            val resistanceTestData = characteristic.readResistanceTest(gatt?.device)
                            resistanceTestPublisher.onNext(resistanceTestData)
                        } else {
                            bluetoothDeviceDataPublisher.onNext(characteristic.readSCharacteristic(gatt?.device))
                        }
                    } else if (characteristic?.uuid == HISTORIC_DATA_UUID) {
                        gatt?.let {
                            val historicData = characteristic.readHistoricData(it.device)
                            historicDataPublisher?.onNext(historicData)
                            if (historicData.packageNumber != 7)
                                mTaskExecutor.setHistoryDescriptor(it)
                            else {
                                historicDataPublisher?.onComplete()
                            }
                        }
                    }
                }
                else -> Log.e(tag, "onCharacteristicRead failed, S code:$S")
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, S: Int) {
            super.onCharacteristicWrite(gatt, characteristic, S)
            when (S) {
                BluetoothGatt.GATT_SUCCESS -> {
                    gatt?.let {
                        it.logOnCharacteristicWrite(characteristic?.uuid)
                        characteristic?.let {
                            when (it.uuid) {
                                BluetoothService.WRITE_CURATION_UUID -> mTaskExecutor.setDescriptorForCharacteristic(gatt, gatt.getCCharacteristic())
                                BluetoothService.WRITE_S_UUID -> mTaskExecutor.setDescriptorForCharacteristic(gatt, gatt.getSCharacteristic())
                            }
                        }
                    }
                }
                else -> Log.e(tag, "onCharacteristicWrite failed, S code:$S")
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, S: Int) {
            super.onDescriptorWrite(gatt, descriptor, S)
            gatt?.let {
                if (descriptor?.characteristic != null) {
                    mTaskExecutor.readCharacteristic(it, descriptor.characteristic)
                }
            }
        }
    }

    companion object {
        const val BLUETOOTH_DEVICE = "bluetoothDevice"
        const val SAVED_DEVICES = "savedDevices"
        const val BLUETOOTH_CHARACTERISTIC = "bluetoothCharacteristic"
        const val ACTION_ENABLE_BLUETOOTH_REQUIRED = "*****"
        const val ACTION_DEVICE_DISCOVERED = "*****"
        const val ACTION_SAVED_DEVICES = "*****"
        const val ACTION_SCAN_COMPLETE = "*****"
        const val ACTION_DEVICE_CONNECTED = "*****"
        const val ACTION_DEVICE_DISCONNECTED = "*****"
        const val ACTION_DEVICE_CHARACTERISTIC_READ = "*****"
        const val ACTION_DEVICE_CHARACTERISTIC_WRITE = "*****"
        const val ACTION_NO_PAIRED_DEVICES = "*****"
        const val ACTION_DEVICES_DISCONNECTED = "*****"

        /**
         * Applicator service UUID's
         */
        val APPLICATOR_SERVICE_UUID: UUID =  //service that provides characteristics

        val READ_CURATION_UUID: UUID =
        val READ_S_UUID: UUID =
        val HISTORIC_DATA_UUID: UUID =

        val WRITE_S_UUID: UUID = ///max 6byte data
        val WRITE_CURATION_UUID: UUID =  //max 25 byte data

        val DESCRIPTOR_UUID: UUID = //notify the device you want to read its characteristic

        fun provideServiceIntent(context: Context): Intent {
            return Intent(context, BluetoothService::class.java)
        }
    }

    /**
     * Binder class that provides communication interface with BluetoothService
     */
    inner class ServiceBinder : Binder() {

        fun getTestPublisher() = resistanceTestPublisher
        fun getBluetoothDeviceDataPublisher() = bluetoothDeviceDataPublisher
        fun getConnectedDevicesPublisher() = connectedDevicePublisher
        fun getHistoricDataPublisher(): PublishSubject<HistoricData>? {
            historicDataPublisher = PublishSubject.create()
            return historicDataPublisher
        }

        fun initializeScan() = startScanning()
        fun abortScanning() = stopScanning()

        fun getDevicesFromSavedAddresses(deviceAddresses: Set<String>) {
            val devices = arrayListOf<BluetoothDevice>()
            val iterator = deviceAddresses.iterator()
            while (iterator.hasNext()) {
                try {
                    val device = mBluetoothManager.adapter.getRemoteDevice(iterator.next())
                    devices.add(device)
                } catch (e: IllegalArgumentException) {
                    e.printStackTrace()
                }
            }
            broadcastSavedDevices(devices)
        }

        fun disconnectDevices() {
            for (connected in connectedDevices) {
                Log.e(tag, "Disconnecting device: ${connected.device}")
                mTaskExecutor.disconnectDevice(connected)
            }
        }

        fun abortReading() {
            if (isReadingActive) {
                isReadingActive = true
                stopReadLoop()
            }
        }

        fun resumeReading() {
            if (!isReadingActive) {
                isReadingActive = false
                startReadLoopTimer()
            }
        }

        private fun broadcastSavedDevices(devices: ArrayList<BluetoothDevice>) {
            val intent = Intent(ACTION_SAVED_DEVICES)
            intent.putParcelableArrayListExtra(SAVED_DEVICES, devices)
            sendBroadcast(intent)
        }

        fun connectWithDevices(deviceList: List<BluetoothDevice>, blockReadingLoop: Boolean = false) {
            numberOfDevices = deviceList.size
            //this@BluetoothService.isReadingActive = isReadingActive
            serviceDiscoveryCountDownLatch = CountDownLatch(deviceList.size)
            val iterator = deviceList.iterator()
            while (iterator.hasNext()) {
                val device = iterator.next()
                Log.e(tag, "Connecting with device: $device")
                mTaskExecutor.connectWithDevice(device)
            }
        }

        fun startDevice(device: BluetoothDevice, @WriteDeviceS S: String) {
            mTaskExecutor.startOrStopStimulation(device, S)
        }

        fun startDevices(@WriteDeviceS S: String) {
            for (connected in connectedDevices)
                mTaskExecutor.startOrStopStimulation(connected.device, S)
        }

        fun readDeviceWhileTestMode(device: BluetoothDevice) {
            val gatt = connectedDevices.first { it.device == device }
            mTaskExecutor.setSDescriptor(gatt)
        }

        fun readHistoricData(device: BluetoothDevice) {
            val gatt = connectedDevices.first { it.device == device }
            mTaskExecutor.setHistoryDescriptor(gatt)
        }

        fun turnTestModeForDevice(device: BluetoothDevice) {
            Log.e(tag, "Starting test mode for $device")
            mTaskExecutor.testMode(device)
            val gatt = connectedDevices.find { it.device == device }
            gatt?.let {
                mTaskExecutor.setSDescriptor(gatt)
            }
        }

        fun stopTestMode(device: BluetoothDevice) {
            testTimer?.cancel()
            testTimer = null
            turnDevice(device, DeviceSes.TURN_OFF)
        }

        fun turnDevice(device: BluetoothDevice, @WriteDeviceS S: String) {
            mTaskExecutor.turnDeviceOnOff(device, S)
        }

        fun turnDevices(@WriteDeviceS S: String) {
            for (connected in connectedDevices)
                mTaskExecutor.turnDeviceOnOff(connected.device, S)
        }

        fun cDevices() {
            for (connected in connectedDevices)
                mTaskExecutor.writeCCharacteristic(connected.device, connected.getWriteCCharacteristic())
        }

        fun cDevice(device: BluetoothDevice) {
            val gatt = connectedDevices.find { it.device == device }
            gatt?.let {
                mTaskExecutor.writeCCharacteristic(device, gatt.getWriteCCharacteristic())
            }
        }
    }
}
