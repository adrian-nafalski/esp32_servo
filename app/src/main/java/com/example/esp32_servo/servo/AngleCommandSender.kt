package com.example.esp32_servo.servo

import com.example.esp32_servo.data.SendResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch

/** Komenda kąta przypisana do konkretnej sesji połączenia. */
data class AngleCommand(val session: Long, val angle: Int)

/**
 * Oddziela wysyłanie BLE od odświeżania UI i ogranicza częstotliwość komend.
 *
 * - Pierwsza komenda wychodzi od razu, każda kolejna najwcześniej [minIntervalMs]
 *   po STARCIE poprzedniej.
 * - Przechowywana jest wyłącznie najnowsza niewysłana wartość ("latest wins"):
 *   wartości nadpisane w czasie oczekiwania przepadają, więc nie rośnie kolejka
 *   nieaktualnych kątów.
 * - Wysyłka jest sekwencyjna: następny zapis zaczyna się dopiero po zakończeniu
 *   poprzedniego (potwierdzenie, błąd albo przekroczenie czasu po stronie BLE).
 * - [clearPending] usuwa niewysłaną wartość, np. po utracie połączenia.
 */
class AngleCommandSender(
    scope: CoroutineScope,
    private val minIntervalMs: Long,
    private val transmit: suspend (AngleCommand) -> SendResult,
    private val onResult: (AngleCommand, SendResult) -> Unit,
) {
    private val latest = MutableStateFlow<AngleCommand?>(null)

    @Volatile
    private var inFlight: AngleCommand? = null

    /** Czy czeka niewysłana komenda (nie liczy komendy właśnie wysyłanej). */
    val hasPending: Boolean get() = latest.value != null

    init {
        scope.launch { sendLoop() }
    }

    fun submit(command: AngleCommand) {
        latest.value = command
    }

    fun clearPending() {
        latest.value = null
    }

    /** Czy ta komenda czeka w kolejce albo właśnie jest wysyłana. */
    fun isQueuedOrInFlight(command: AngleCommand): Boolean =
        latest.value == command || inFlight == command

    private suspend fun sendLoop() {
        while (true) {
            latest.first { it != null }
            val command = latest.getAndUpdate { null } ?: continue
            coroutineScope {
                // Odstęp liczony od startu zapisu; równoległe odliczanie nie opóźnia samego zapisu.
                launch { delay(minIntervalMs) }
                inFlight = command
                val result = try {
                    transmit(command)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    SendResult.Rejected(code = -1)
                } finally {
                    inFlight = null
                }
                onResult(command, result)
            }
        }
    }
}
