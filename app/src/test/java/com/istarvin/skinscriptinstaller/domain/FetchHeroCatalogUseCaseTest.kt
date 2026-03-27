package com.istarvin.skinscriptinstaller.domain

import com.istarvin.skinscriptinstaller.data.catalog.HeroCatalogFallbackAssetDataSource
import com.istarvin.skinscriptinstaller.data.network.api.HeroApiService
import com.istarvin.skinscriptinstaller.data.network.dto.HeroCatalogData
import com.istarvin.skinscriptinstaller.data.network.dto.HeroCatalogHeroData
import com.istarvin.skinscriptinstaller.data.network.dto.HeroCatalogHeroWrapper
import com.istarvin.skinscriptinstaller.data.network.dto.HeroCatalogRecord
import com.istarvin.skinscriptinstaller.data.network.dto.HeroCatalogRecordData
import com.istarvin.skinscriptinstaller.data.network.dto.HeroCatalogResponse
import com.istarvin.skinscriptinstaller.data.repository.ScriptRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FetchHeroCatalogUseCaseTest {

    private lateinit var heroApiService: HeroApiService
    private lateinit var repository: ScriptRepository
    private lateinit var fallbackAssetDataSource: HeroCatalogFallbackAssetDataSource
    private lateinit var useCase: FetchHeroCatalogUseCase

    @Before
    fun setUp() {
        heroApiService = mockk()
        repository = mockk(relaxed = true)
        fallbackAssetDataSource = mockk()
        useCase = FetchHeroCatalogUseCase(
            heroApiService = heroApiService,
            repository = repository,
            heroCatalogFallbackAssetDataSource = fallbackAssetDataSource
        )
    }

    @Test
    fun `execute syncs API items on success`() = runTest {
        val response = HeroCatalogResponse(
            data = HeroCatalogData(
                records = listOf(
                    HeroCatalogRecord(
                        data = HeroCatalogRecordData(
                            hero = HeroCatalogHeroWrapper(
                                data = HeroCatalogHeroData(
                                    head = "https://cdn/icon-miya.png",
                                    name = "Miya"
                                )
                            )
                        )
                    )
                )
            )
        )

        coEvery { heroApiService.getHeroes(any()) } returns response

        val result = useCase.execute()

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())
        coVerify(exactly = 1) { repository.syncHeroCatalog(listOf("Miya" to "https://cdn/icon-miya.png")) }
        coVerify(exactly = 0) { repository.getHeroCount() }
        coVerify(exactly = 0) { fallbackAssetDataSource.loadItems() }
    }

    @Test
    fun `execute loads fallback when API fails and hero DB is empty`() = runTest {
        coEvery { heroApiService.getHeroes(any()) } throws RuntimeException("network down")
        coEvery { repository.getHeroCount() } returns 0
        every { fallbackAssetDataSource.loadItems() } returns listOf(
            "Layla" to "https://cdn/icon-layla.png"
        )

        val result = useCase.execute()

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())
        coVerify(exactly = 1) { repository.getHeroCount() }
        coVerify(exactly = 1) { repository.syncHeroCatalog(listOf("Layla" to "https://cdn/icon-layla.png")) }
        coVerify(exactly = 1) { heroApiService.getHeroes(any()) }
    }

    @Test
    fun `execute does not load fallback when API fails and hero DB is not empty`() = runTest {
        coEvery { heroApiService.getHeroes(any()) } throws RuntimeException("network down")
        coEvery { repository.getHeroCount() } returns 12

        val result = useCase.execute()

        assertTrue(result.isFailure)
        coVerify(exactly = 1) { repository.getHeroCount() }
        coVerify(exactly = 0) { repository.syncHeroCatalog(any()) }
        coVerify(exactly = 0) { fallbackAssetDataSource.loadItems() }
    }
}
