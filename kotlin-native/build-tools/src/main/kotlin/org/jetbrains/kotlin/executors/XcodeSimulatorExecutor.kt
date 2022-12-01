/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.executors

import org.jetbrains.kotlin.konan.target.*
import org.jetbrains.kotlin.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.logging.Logger

private fun defaultDeviceId(target: KonanTarget) = when (target.family) {
    Family.TVOS -> "com.apple.CoreSimulator.SimDeviceType.Apple-TV-4K-4K"
    Family.IOS -> "com.apple.CoreSimulator.SimDeviceType.iPhone-14"
    Family.WATCHOS -> "com.apple.CoreSimulator.SimDeviceType.Apple-Watch-Series-6-40mm"
    else -> error("Unexpected simulation target: $target")
}

private fun Executor.run(executableAbsolutePath: String, vararg args: String) = ByteArrayOutputStream().let {
    this.execute(executeRequest(executableAbsolutePath).apply {
        this.args.addAll(args)
        stdout = it
        workingDirectory = File("").absoluteFile
    }).assertSuccess()
    it
}

/**
 * [Executor] that runs the process in an Xcode simulator.
 *
 * @param configurables [Configurables] for simulated target
 * @property deviceName which simulator to use (optional). When not provided the default simulator for [configurables.target] is used
 */
class XcodeSimulatorExecutor(
        private val configurables: AppleConfigurables,
        var deviceId: String = defaultDeviceId(configurables.target),
) : Executor {
    private val hostExecutor: Executor = HostExecutor()

    private val logger = Logger.getLogger(this::class.java.name)

    private val target by configurables::target

    init {
        require(configurables.targetTriple.isSimulator) {
            "$target is not a simulator."
        }
        val hostArch = HostManager.host.architecture
        val targetArch = target.architecture
        val compatibleArchs = when (hostArch) {
            Architecture.X64 -> listOf(Architecture.X64, Architecture.X86)
            Architecture.ARM64 -> listOf(Architecture.ARM64, Architecture.ARM32)
            else -> throw IllegalStateException("$hostArch is not a supported host architecture for the simulator")
        }
        require(targetArch in compatibleArchs) {
            "Can't run simulator for $targetArch architecture on $hostArch host architecture"
        }
    }

    private val archSpecification = when (target.architecture) {
        Architecture.X86 -> listOf("-a", "i386")
        Architecture.X64 -> listOf() // x86-64 is used by default on Intel Macs.
        Architecture.ARM64 -> listOf() // arm64 is used by default on Apple Silicon.
        else -> error("${target.architecture} can't be used in simulator.")
    }.toTypedArray()

    private fun simctl(vararg args: String): String {
        val out = hostExecutor.run("/usr/bin/xcrun", *arrayOf("simctl", *args))
        return out.toString("UTF-8").trim()
    }

    private var deviceChecked: SimulatorDeviceDescriptor? = null

    private fun ensureSimulatorExists() {
        // Already ensured that simulator for `deviceName` exists.
        if (deviceId == deviceChecked?.deviceTypeIdentifier) {
            println("Device already exists: ${deviceChecked?.deviceTypeIdentifier} with name ${deviceChecked?.name}")
            return
        }
        println("Let's find the device")
        // Find out if the default device is available
        val osName = simulatorOsName(target.family)
        // Get the available simulator runtimes
        val runtimeArgs = arrayOf("list", "runtimes", "--json")
        var simulatorRuntimeList = getSimulatorRuntimesFor(
                json = simctl(*runtimeArgs),
                family = target.family,
                osMinVersion = configurables.osVersionMin
        )
        if (!simulatorRuntimeList.isEmpty()) {
            // Download this platform if not available
            hostExecutor.run("/usr/bin/xcrun", "xcodebuild", "-downloadPlatform", osName)
            // Now get it
            simulatorRuntimeList = getSimulatorRuntimesFor(
                    json = simctl(*runtimeArgs),
                    family = target.family,
                    osMinVersion = configurables.osVersionMin
            )
        }
        check(!simulatorRuntimeList.isEmpty()) { "Unable to get simulator runtime for $target. Check Xcode installation" }

        val simulatorRuntime = simulatorRuntimeList.firstOrNull {
            it.supportedDeviceTypes.any { deviceType -> deviceType.identifier == deviceId }
        } ?: error("Runtime is not available for the selected $deviceId")
        println("Runtime used for the $deviceId")
        println(simulatorRuntime)

        val devicesListArgs = arrayOf("list", "devices", "--json")
        val runtimeIdentifier = simulatorRuntime.identifier
        var devices = getSimulatorDevices(simctl(*devicesListArgs)).get(runtimeIdentifier)
        if (devices == null || devices.isEmpty()) {
            // Create device if not available
            val deviceType = simulatorRuntime.supportedDeviceTypes.find { it.identifier == deviceId }
            checkNotNull(deviceType) {
                """
                    Default device $deviceId is not available for the runtme: ${simulatorRuntime.name}
                    Supported devices: [${simulatorRuntime.supportedDeviceTypes.map { it.identifier }.joinToString(", ")}]
                """.trimIndent()
            }
            simctl("create", deviceType.name, deviceType.identifier, runtimeIdentifier)
            devices = getSimulatorDevices(simctl(*devicesListArgs)).get(runtimeIdentifier)
        }
        checkNotNull(devices) { "Unable to get or create simulator device $deviceId for $target with runtime ${simulatorRuntime.name}" }

        val device = devices.find { it?.deviceTypeIdentifier == deviceId && it.isAvailable == true }
        println("Found device: $device")
        // If successfully created, remember that.
        deviceChecked = device
    }

    override fun execute(request: ExecuteRequest): ExecuteResponse {
        ensureSimulatorExists()
        val executable = request.executableAbsolutePath
        val env = request.environment.mapKeys {
            "SIMCTL_CHILD_" + it.key
        }
        val workingDirectory = request.workingDirectory ?: File(request.executableAbsolutePath).parentFile
        val name = deviceChecked?.name ?: error("No device available for $deviceId")
        // Starting Xcode 11 `simctl spawn` requires explicit `--standalone` flag.
        return hostExecutor.execute(request.copying {
            this.executableAbsolutePath = "/usr/bin/xcrun"
            this.workingDirectory = workingDirectory
            this.args.addAll(0, listOf("simctl", "spawn", "--standalone", *archSpecification, name, executable))
            this.environment.clear()
            this.environment.putAll(env)
        })
    }
}