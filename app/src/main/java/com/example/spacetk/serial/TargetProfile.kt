package com.example.spacetk.serial

enum class SerialParity { NONE, ODD, EVEN }

/**
 * Per-board UART settings. Baud rate and framing differ per target (see openobsw's README),
 * so the decoder/terminal must be driven by a profile rather than hardcoded constants.
 */
data class TargetProfile(
    val name: String,
    val baudRate: Int,
    val dataBits: Int = 8,
    val stopBits: Int = 1,
    val parity: SerialParity = SerialParity.NONE,
    val isCustom: Boolean = false,
) {
    companion object {
        val Stm32H750 = TargetProfile(name = "STM32H750 USART3", baudRate = 115200)
        val Msp430 = TargetProfile(name = "MSP430FR5969 UCA0", baudRate = 9600)
        val GenericZynqMp = TargetProfile(name = "Generic / ZynqMP console", baudRate = 115200)
        val Custom = TargetProfile(name = "Custom", baudRate = 115200, isCustom = true)

        val builtIn: List<TargetProfile> = listOf(Stm32H750, Msp430, GenericZynqMp, Custom)
    }
}
