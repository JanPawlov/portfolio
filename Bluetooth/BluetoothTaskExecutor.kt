
import android.bluetooth.*
import android.content.Context
import android.util.Log
import java.util.*
import java.util.concurrent.*

/**
 * Created by janpawlov ( ͡° ͜ʖ ͡°) on 07/08/2018.
 *
 * Executor class that handles all bluetooth commands
 * Because BluetoothGatt has no form of queuing called operations, calling
 *
 * gatt.operation1()
 * gatt.operation2()
 *
 * immediately one after another would result in operation2 overriding operation1,
 * hence preventing operation1 from completing
 *
 * This create necessity of queueing GATT operations and introducing delay
 * between them to ensure their completion
 *
 * Bluetooth operations are put into @param mWorkQueue
 * and executed by @param mThreadPoolExecutor
 */
class BluetoothTaskExecutor(val context: Context,
                            private val bluetoothGattCallback: BluetoothGattCallback,
                            private val connectedDevices: MutableList<BluetoothGatt>) {

    private val tag = this.javaClass.simpleName
    private val BLUETOOTH_DELAY: Long = 350
    private val SHORT_BLUETOOTH_DELAY: Long = 200

    private val numberOfCores = Runtime.getRuntime().availableProcessors()
    private val keepAliveTime: Long = 1
    private val mWorkQueue: BlockingQueue<Runnable> = LinkedBlockingQueue<Runnable>()
    private val mThreadPoolExecutor = ThreadPoolExecutor(numberOfCores,
            numberOfCores,
            keepAliveTime,
            TimeUnit.SECONDS,
            mWorkQueue)

    fun connectWithDevice(device: BluetoothDevice) {
        mWorkQueue.put(Runnable {
            Log.d(tag, "Connecting to a device: ${device.address}")
            device.connectGatt(context, false, bluetoothGattCallback, BluetoothDevice.TRANSPORT_LE)
        })
        executeNextTask()
    }

    fun reattemptConnect(gatt: BluetoothGatt) {
        mWorkQueue.put(Runnable {
            gatt.disconnect()
            delay()
            Log.e(tag, "Reattempting connection with device: ${gatt.device.address}")
            gatt.device.connectGatt(context, false, bluetoothGattCallback, BluetoothDevice.TRANSPORT_LE)
        })
        executeNextTask()
    }

    fun discoverServices(gatt: BluetoothGatt) {
        mWorkQueue.put(Runnable {
            Log.d(tag, "Discovering services for device: ${gatt.device.address}")
            gatt.discoverServices()
        })
        executeNextTask()
    }

    fun readCharacteristic(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        mWorkQueue.put(Runnable {
            val charType = when (characteristic.uuid) {
                BluetoothService.READ_sUS_UUID -> "sus"
                BluetoothService.READ_cURATION_UUID -> "c"
                BluetoothService.h_DATA_UUID -> "h"
                else -> ""
            }
            var read = gatt.readCharacteristic(characteristic)
            while (!read) {
                delay(SHORT_BLUETOOTH_DELAY)
                read = gatt.readCharacteristic(characteristic)
            }
        })
        executeNextTask()
    }

    fun setDescriptors(gatt: BluetoothGatt) {
        mWorkQueue.put(Runnable {
            val service = gatt.getService(BluetoothService.SERVICE_UUID)
            val cChar = service.getCharacteristic(BluetoothService.READ_C_UUID)
            val sChar = service.getCharacteristic(BluetoothService.READ_S_UUID)
            val cDescriptor = cChar.getDescriptor(BluetoothService.DESCRIPTOR_UUID)
            val sDescriptor = sChar.getDescriptor(BluetoothService.DESCRIPTOR_UUID)
            cDescriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            var write = gatt.writeDescriptor(cDescriptor)
            while (!write) {
                delay(SHORT_BLUETOOTH_DELAY)
                write = gatt.writeDescriptor(cDescriptor)
            }
            susDescriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            write = gatt.writeDescriptor(sDescriptor)
            while (!write) {
                delay(SHORT_BLUETOOTH_DELAY)
                write = gatt.writeDescriptor(sDescriptor)
            }
        })
        executeNextTask()
    }

    fun sethDescriptor(gatt: BluetoothGatt) {
        mWorkQueue.put(Runnable {
            val service = gatt.getService(BluetoothService.DEVICE_SERVICE_UUID)
            if (service != null) {
                val susChar = service.getCharacteristic(BluetoothService.h_DATA_UUID)
                val susDescriptor = susChar.getDescriptor(BluetoothService.DESCRIPTOR_UUID)
                susDescriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                var write = gatt.writeDescriptor(susDescriptor)
                while (!write) {
                    delay()
                    write = gatt.writeDescriptor(susDescriptor)
                }
            }
        })
        executeNextTask()
    }

    fun setcDescriptor(gattList: MutableList<BluetoothGatt>) {
        mWorkQueue.put(Runnable {
            for (gatt in gattList) {
                val service = gatt.getService(BluetoothService.DEVICE_SERVICE_UUID)
                val cChar = service.getCharacteristic(BluetoothService.READ_cURATION_UUID)
                val cDescriptor = cChar.getDescriptor(BluetoothService.DESCRIPTOR_UUID)
                cDescriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                var write = gatt.writeDescriptor(cDescriptor)
                while (!write) {
                    delay(SHORT_BLUETOOTH_DELAY)
                    write = gatt.writeDescriptor(cDescriptor)
                }
            }
        })
        executeNextTask()
    }

    fun setsusDescriptor(gatt: BluetoothGatt) {
        mWorkQueue.put(Runnable {
            val service = gatt.getService(BluetoothService.DEVICE_SERVICE_UUID)
            if (service != null) {
                val susChar = service.getCharacteristic(BluetoothService.READ_sUS_UUID)
                val susDescriptor = susChar.getDescriptor(BluetoothService.DESCRIPTOR_UUID)
                susDescriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                var write = gatt.writeDescriptor(susDescriptor)
                while (!write) {
                    delay()
                    write = gatt.writeDescriptor(susDescriptor)
                }
            }
        })
        executeNextTask()
    }

    fun setsusDescriptors(gattList: MutableList<BluetoothGatt>) {
        mWorkQueue.put(Runnable {
            for (gatt in gattList) {
                val service = gatt.getService(BluetoothService.DEVICE_SERVICE_UUID)
                if (service != null) {
                    val susChar = service.getCharacteristic(BluetoothService.READ_sUS_UUID)
                    val susDescriptor = susChar.getDescriptor(BluetoothService.DESCRIPTOR_UUID)
                    susDescriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    var write = gatt.writeDescriptor(susDescriptor)
                    if (!write) {
                        for (i in 0..10) {
                            if (gatt.writeDescriptor(susDescriptor))
                                break
                        }
                    }
                }
            }
        })
        executeNextTask()
    }

    /**
     * Set descriptor for given characteristic
     */
    fun setDescriptorForCharacteristic(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        mWorkQueue.put(Runnable {
            val descriptor = characteristic.getDescriptor(BluetoothService.DESCRIPTOR_UUID)
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            var write = gatt.writeDescriptor(descriptor)
            while (!write) {
                delay(SHORT_BLUETOOTH_DELAY)
                write = gatt.writeDescriptor(descriptor)
            }
        })
        executeNextTask()
    }

    private var disconnectCountDownLatch: CountDownLatch? = null
    private var disconnectionThread: Thread? = null

    fun disconnectDevice(gatt: BluetoothGatt) {
        if (disconnectCountDownLatch == null)
            disconnectCountDownLatch = CountDownLatch(connectedDevices.size)
        if (disconnectionThread == null) {
            disconnectionThread = Thread {
                try {
                    disconnectCountDownLatch?.await()
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
                disconnectionThread?.interrupt()
                disconnectionThread = null
            }
            disconnectionThread?.start()
        }
        mWorkQueue.put(Runnable {
            gatt.disconnect()
        })
        executeNextTask()
    }

    fun countDownLatch() = disconnectCountDownLatch?.countDown()

    fun turnDeviceOnOff(device: BluetoothDevice, @WriteDevicesus sus: String) {
        mWorkQueue.put(Runnable {
            val gatt = connectedDevices.find { it.device == device }
            gatt?.let {
                val service = gatt.getService(BluetoothService.DEVICE_SERVICE_UUID)
                val sCharacteristic = service.getCharacteristic(BluetoothService.WRITE_sUS_UUID)
                sCharacteristic.value = sus.toByteArray(Charsets.US_ASCII)
                var write = it.writeCharacteristic(sCharacteristic)
                while (!write) {
                    delay(SHORT_BLUETOOTH_DELAY)
                    write = it.writeCharacteristic(sCharacteristic)
                }
            }
        })
        executeNextTask()
    }

    fun testMode(device: BluetoothDevice) {
        mWorkQueue.put(Runnable {
            val gatt = connectedDevices.find { it.device == device }
            gatt?.let {
                val service = gatt.getService(BluetoothService.DEVICE_SERVICE_UUID)
                val sCharacteristic = service.getCharacteristic(BluetoothService.WRITE_sUS_UUID)
                val command = Devicesuses.TEST_MODE
                sCharacteristic.value = command.toByteArray(Charsets.US_ASCII)
                var write = it.writeCharacteristic(sCharacteristic)
                while (!write) {
                    delay(SHORT_BLUETOOTH_DELAY)
                    write = it.writeCharacteristic(sCharacteristic)
                }
            }
        })
        executeNextTask()
    }

    fun startOrStop(device: BluetoothDevice, @WriteDevicesus sus: String) {
        mWorkQueue.put(Runnable {
            val gatt = connectedDevices.find { it.device == device }
            gatt?.let {
                val service = gatt.getService(BluetoothService.DEVICE_SERVICE_UUID)
                val sCharacteristic = service.getCharacteristic(BluetoothService.WRITE_sUS_UUID)
                val time = Calendar.getInstance().getTimeFromNightTime()
                if (sus == Devicesuses.START && time != null) {
                    val susByte = sus.toByteArray(Charsets.US_ASCII)
                    sCharacteristic.value = ByteArray(susByte.size + 2)
                    sCharacteristic.value[0] = susByte[0]
                    var setValue = sCharacteristic.setValue(time, BluetoothGattCharacteristic.FORMAT_SINT16, susByte.size)
                    while (!setValue) {
                        setValue = sCharacteristic.setValue(time, BluetoothGattCharacteristic.FORMAT_SINT16, susByte.size)
                    }
                } else {
                    sCharacteristic.value = sus.toByteArray(Charsets.US_ASCII)
                    Log.e(tag, "Writing s characteristic size: ${sCharacteristic.value.size}")
                }
                var write = it.writeCharacteristic(sCharacteristic)
                while (!write) {
                    delay(SHORT_BLUETOOTH_DELAY)
                    write = it.writeCharacteristic(sCharacteristic)
                }
            }
        })
        executeNextTask()
    }

    fun clearH(device: BluetoothDevice) {
        mWorkQueue.put(Runnable {
            val gatt = connectedDevices.find { it.device == device }
            gatt?.let {
                val service = gatt.getService(BluetoothService.DEVICE_SERVICE_UUID)
                val sCharacteristic = service.getCharacteristic(BluetoothService.WRITE_sUS_UUID)
                Log.d(tag, "Writing sus characteristic for device: ${device.address}")
                val command = Devicesuses.CLEAR_H
                sCharacteristic.value = command.toByteArray(Charsets.US_ASCII)
                var write = it.writeCharacteristic(sCharacteristic)
                while (!write) {
                    delay(SHORT_BLUETOOTH_DELAY)
                    write = it.writeCharacteristic(sCharacteristic)
                }
            }
        })
        executeNextTask()
    }

    fun writeCharacteristic(device: BluetoothDevice, characteristic: BluetoothGattCharacteristic) {
        mWorkQueue.put(Runnable {
            val gatt = connectedDevices.find { it.device == device }
            gatt?.let {
                val cBox = MainApp.storeBox.boxFor(curationData::class.java)
                val cData = cBox.query().order(curationData_.id).build().find().first()
                val devicecBox = MainApp.storeBox.boxFor(Devicec::class.java)
                val devicec = devicecBox.query().equal(Devicec_.address, device.address).build().findUnique()
                if (devicec != null && cData != null) {
                    val stimulationTime = cData.workTime
                    val restTime = cData.restTime
                    val cIndex = cData.curation
                    val dayCurrent = devicec.dayCurrent
                    val nightCurrent = devicec.nightCurrent
                    if (stimulationTime != null && restTime != null && cIndex != null && dayCurrent != null && nightCurrent != null) {
                        val frequency = Stms.stimulationLists[cIndex]
                        val c = arrayListOf<Int>()
                        c.add(restTime)
                        c.add(stimulationTime)
                        c.addAll(frequency)
                        c.add(dayCurrent)
                        c.add(nightCurrent)
                        cCharacteristic.writeParameters(c)
                        Log.e(device.name, "Writing c $c")
                        var write = it.writeCharacteristic(cCharacteristic)
                        while (!write) {
                            delay(SHORT_BLUETOOTH_DELAY)
                            write = it.writeCharacteristic(cCharacteristic)
                        }
                    } else {
                        Log.e(tag, "Incomplete c data, aborting c write")
                    }
                } else {
                    Log.e(tag, "Could not find c for device ${device.address}, aborting c write")
                }
            }
        })
        executeNextTask()
    }

    private fun delay(delay: Long = BLUETOOTH_DELAY) {
        try {
            Thread.sleep(delay)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    private fun executeNextTask() {
        delay()
        mWorkQueue.poll()?.let {
            mThreadPoolExecutor.execute(it)
        }
    }

}
