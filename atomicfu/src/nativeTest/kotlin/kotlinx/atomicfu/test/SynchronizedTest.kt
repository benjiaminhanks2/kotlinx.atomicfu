package kotlinx.atomicfu.test

import kotlinx.atomicfu.*
import kotlin.native.concurrent.*
import kotlin.native.concurrent.AtomicInt
import kotlin.test.*

private const val iterations = 100
private const val nWorkers = 4
private const val increments = 500
private const val nLocks = 5

class SynchronizedTest {

    @Test
    fun stressCounterTest() {
        repeat(iterations) {
            val workers = Array(nWorkers) { Worker.start() }
            val counter = AtomicInt(0).freeze()
            val so = SynchronizedObject().freeze()
            workers.forEach { worker ->
                worker.execute(TransferMode.SAFE, {
                    counter to so
                }) { (count, lock) ->
                    repeat(increments) {
                        val nestedLocks = (1..3).random()
                        repeat(nestedLocks) { lock.lock() }
                        val oldValue = count.value
                        count.value = oldValue + 1
                        repeat(nestedLocks) { lock.unlock() }
                    }
                }
            }
            workers.forEach {
                it.requestTermination().result
            }
            assertEquals(nWorkers * increments, counter.value)
        }
    }


    @Test
    fun manyLocksTest() {
        repeat(iterations) { 
            val workers = Array(nWorkers) { Worker.start() }
            val counters = Array(nLocks) { AtomicInt(0) }.freeze()
            val locks = Array(nLocks) { SynchronizedObject() }.freeze()
            workers.forEach { worker ->
                worker.execute(TransferMode.SAFE, {
                    counters to locks
                }) { (counters, locks) ->
                    locks.forEachIndexed { i, lock ->
                        repeat(increments) {
                            synchronized(lock) {
                                val oldValue = counters[i].value
                                counters[i].value = oldValue + 1
                            }
                        }
                    }
                }
            }
            workers.forEach {
                it.requestTermination().result
            }
            assertEquals(nWorkers * nLocks * increments, counters.sumBy { it.value })
        }
    }
}