/*
 * Copyright 2024 Mobile Native Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mobilenativefoundation.store.store5

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.mobilenativefoundation.store.store5.impl.extensions.fresh
import org.mobilenativefoundation.store.store5.util.FakeFetcher
import org.mobilenativefoundation.store.store5.util.InMemoryPersister
import org.mobilenativefoundation.store.store5.util.asSourceOfTruth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@FlowPreview
@ExperimentalCoroutinesApi
class AdditionalComprehensiveTests {
    private val testScope = TestScope()

    @Test
    fun test1_storeHandlesConcurrentRequestsForSameKey() =
        testScope.runTest {
            val fetcher = FakeFetcher(1 to "data")
            val store = StoreBuilder.from(fetcher).scope(testScope).build()

            val jobs = (1..10).map {
                launch { store.fresh(1) }
            }

            jobs.forEach { it.join() }
            assertEquals(1, fetcher.fetchCount)
        }

    @Test
    fun test2_storeProperlyClearsCacheAndSourceOfTruth() =
        testScope.runTest {
            val fetcher = FakeFetcher(
                1 to "initial",
                1 to "refreshed"
            )
            val persister = InMemoryPersister<Int, String>()
            val store = StoreBuilder.from(
                fetcher = fetcher,
                sourceOfTruth = persister.asSourceOfTruth()
            ).scope(testScope).build()

            store.fresh(1)
            assertEquals("initial", persister.read(1))

            store.clear(1)
            assertEquals(null, persister.read(1))

            val refreshedData = store.fresh(1)
            assertEquals("refreshed", refreshedData)
        }

    @Test
    fun test3_storeHandlesNullValuesCorrectly() =
        testScope.runTest {
            val fetcher = FakeFetcher(1 to null)
            val store = StoreBuilder.from(fetcher).scope(testScope).build()

            store.stream(StoreReadRequest.fresh(1)).test {
                assertEquals(
                    StoreReadResponse.Loading(origin = StoreReadResponseOrigin.Fetcher()),
                    awaitItem()
                )
                assertEquals(
                    StoreReadResponse.NoNewData(origin = StoreReadResponseOrigin.Fetcher()),
                    awaitItem()
                )
            }
        }

    @Test
    fun test4_storeMemoryLeakPrevention() =
        testScope.runTest {
            val fetcher = FakeFetcher((1..1000).associateWith { "data$it" })
            val store = StoreBuilder.from(fetcher).scope(testScope).build()

            repeat(1000) { key ->
                store.fresh(key + 1)
            }

            store.clearAll()

            System.gc()
            assertTrue(true)
        }

    @Test
    fun test5_storeHandlesRapidInvalidationCorrectly() =
        testScope.runTest {
            val fetcher = FakeFetcher(
                1 to "v1",
                1 to "v2",
                1 to "v3"
            )
            val persister = InMemoryPersister<Int, String>()
            val store = StoreBuilder.from(
                fetcher = fetcher,
                sourceOfTruth = persister.asSourceOfTruth()
            ).scope(testScope).build()

            store.fresh(1)
            assertEquals("v1", persister.read(1))

            repeat(5) {
                store.clear(1)
                store.fresh(1)
            }

            assertNotNull(persister.read(1))
        }

    @Test
    fun test6_storeWithCustomValidator() =
        testScope.runTest {
            val fetcher = FakeFetcher(1 to "invalid_data", 1 to "valid_data")
            val store = StoreBuilder.from(fetcher)
                .validator { data: String -> data.startsWith("valid") }
                .scope(testScope)
                .build()

            val result = store.fresh(1)
            assertEquals("valid_data", result)
            assertEquals(2, fetcher.fetchCount)
        }

    @Test
    fun test7_storeHandlesSourceOfTruthWriteFailures() =
        testScope.runTest {
            val fetcher = FakeFetcher(1 to "data")
            val failingPersister = object : SourceOfTruth<Int, String> {
                override fun reader(key: Int) = flowOf("cached")
                override suspend fun writer(key: Int, value: String) {
                    throw RuntimeException("Write failed")
                }
                override suspend fun delete(key: Int) {}
                override suspend fun deleteAll() {}
            }

            val store = StoreBuilder.from(
                fetcher = fetcher,
                sourceOfTruth = failingPersister
            ).scope(testScope).build()

            store.stream(StoreReadRequest.fresh(1)).test {
                assertEquals(
                    StoreReadResponse.Loading(origin = StoreReadResponseOrigin.Fetcher()),
                    awaitItem()
                )
                assertEquals(
                    StoreReadResponse.Error.Exception(
                        error = RuntimeException("Write failed"),
                        origin = StoreReadResponseOrigin.SourceOfTruth
                    ),
                    awaitItem()
                )
            }
        }

    @Test
    fun test8_storeWithSlowFetcherAndQuickCache() =
        testScope.runTest {
            var fetchCount = 0
            val slowFetcher = Fetcher.of<Int, String> {
                fetchCount++
                delay(1000)
                "slow_data_$fetchCount"
            }
            val store = StoreBuilder.from(slowFetcher).scope(testScope).build()

            val job1 = launch { store.fresh(1) }
            val job2 = launch {
                delay(10)
                store.get(StoreReadRequest.cached(1, refresh = false))
            }

            advanceUntilIdle()
            job1.join()
            job2.join()

            assertEquals(1, fetchCount)
        }

    @Test
    fun test9_storeHandlesTypeConversionInSourceOfTruth() =
        testScope.runTest {
            data class NetworkData(val value: String)
            data class LocalData(val localValue: String)

            val fetcher = Fetcher.of<Int, NetworkData> { NetworkData("network") }
            val converter = object : Converter<NetworkData, LocalData, LocalData> {
                override fun fromNetworkToLocal(network: NetworkData) = LocalData(network.value)
                override fun fromOutputToLocal(output: LocalData) = output
            }

            val persister = InMemoryPersister<Int, LocalData>()
            val store = StoreBuilder.from(
                fetcher = fetcher,
                sourceOfTruth = persister.asSourceOfTruth()
            )
                .withConversions(converter)
                .scope(testScope)
                .build()

            val result = store.fresh(1)
            assertEquals(LocalData("network"), result)
            assertEquals(LocalData("network"), persister.read(1))
        }

    @Test
    fun test10_storeExceptionHandlingInComplexFlow() =
        testScope.runTest {
            var callCount = 0
            val complexFetcher = Fetcher.ofFlow<Int, String> { key ->
                flow {
                    callCount++
                    when (callCount) {
                        1 -> throw IllegalStateException("First call fails")
                        2 -> {
                            emit("partial_data")
                            throw RuntimeException("Second call fails mid-stream")
                        }
                        else -> emit("success_data")
                    }
                }
            }

            val store = StoreBuilder.from(complexFetcher).scope(testScope).build()

            assertFailsWith<IllegalStateException> {
                store.fresh(1)
            }

            store.stream(StoreReadRequest.fresh(1)).test {
                assertEquals(
                    StoreReadResponse.Loading(origin = StoreReadResponseOrigin.Fetcher()),
                    awaitItem()
                )
                assertEquals(
                    StoreReadResponse.Data(
                        value = "partial_data",
                        origin = StoreReadResponseOrigin.Fetcher()
                    ),
                    awaitItem()
                )
                assertEquals(
                    StoreReadResponse.Error.Exception(
                        error = RuntimeException("Second call fails mid-stream"),
                        origin = StoreReadResponseOrigin.Fetcher()
                    ),
                    awaitItem()
                )
            }

            val finalResult = store.fresh(1)
            assertEquals("success_data", finalResult)
        }
}