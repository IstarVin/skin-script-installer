package com.istarvin.skinscriptinstaller.ui.screens.catalogeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.istarvin.skinscriptinstaller.data.db.entity.Hero
import com.istarvin.skinscriptinstaller.data.db.entity.Skin
import com.istarvin.skinscriptinstaller.data.repository.ScriptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CatalogEditorViewModel @Inject constructor(
    private val repository: ScriptRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val allHeroesRaw = repository.getAllHeroes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredHeroes: StateFlow<List<Hero>> = combine(allHeroesRaw, _searchQuery) { heroes, query ->
        if (query.isBlank()) heroes
        else heroes.filter { it.name.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _expandedHeroId = MutableStateFlow<Long?>(null)
    val expandedHeroId: StateFlow<Long?> = _expandedHeroId.asStateFlow()

    private val _skinsForExpandedHero = MutableStateFlow<List<Skin>>(emptyList())
    val skinsForExpandedHero: StateFlow<List<Skin>> = _skinsForExpandedHero.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleHeroExpansion(heroId: Long) {
        if (_expandedHeroId.value == heroId) {
            _expandedHeroId.value = null
            _skinsForExpandedHero.value = emptyList()
        } else {
            _expandedHeroId.value = heroId
            loadSkinsForHero(heroId)
        }
    }

    private fun loadSkinsForHero(heroId: Long) {
        viewModelScope.launch {
            _skinsForExpandedHero.value = repository.getSkinsByHeroIdOnce(heroId)
        }
    }

    fun addHero(name: String) {
        viewModelScope.launch {
            val existing = repository.getHeroByName(name)
            if (existing != null) {
                _message.value = "Hero \"$name\" already exists"
                return@launch
            }
            repository.insertHero(Hero(name = name))
            _message.value = "Added hero \"$name\""
        }
    }

    fun updateHeroName(id: Long, name: String) {
        viewModelScope.launch {
            val existing = repository.getHeroByName(name)
            if (existing != null && existing.id != id) {
                _message.value = "Hero \"$name\" already exists"
                return@launch
            }
            repository.updateHeroName(id, name)
            _message.value = "Renamed hero to \"$name\""
        }
    }

    fun deleteHero(id: Long) {
        viewModelScope.launch {
            repository.deleteHero(id)
            if (_expandedHeroId.value == id) {
                _expandedHeroId.value = null
                _skinsForExpandedHero.value = emptyList()
            }
            _message.value = "Hero deleted"
        }
    }

    fun countScriptsByHeroId(heroId: Long, onResult: (Int) -> Unit) {
        viewModelScope.launch {
            onResult(repository.countScriptsByHeroId(heroId))
        }
    }

    fun addSkin(heroId: Long, name: String) {
        viewModelScope.launch {
            val existing = repository.getSkinByHeroIdAndName(heroId, name)
            if (existing != null) {
                _message.value = "Skin \"$name\" already exists for this hero"
                return@launch
            }
            repository.insertSkin(Skin(heroId = heroId, name = name))
            loadSkinsForHero(heroId)
            _message.value = "Added skin \"$name\""
        }
    }

    fun updateSkinName(id: Long, heroId: Long, name: String) {
        viewModelScope.launch {
            val existing = repository.getSkinByHeroIdAndName(heroId, name)
            if (existing != null && existing.id != id) {
                _message.value = "Skin \"$name\" already exists for this hero"
                return@launch
            }
            repository.updateSkinName(id, name)
            loadSkinsForHero(heroId)
            _message.value = "Renamed skin to \"$name\""
        }
    }

    fun deleteSkin(id: Long, heroId: Long) {
        viewModelScope.launch {
            repository.deleteSkin(id)
            loadSkinsForHero(heroId)
            _message.value = "Skin deleted"
        }
    }

    fun countScriptsBySkinId(skinId: Long, onResult: (Int) -> Unit) {
        viewModelScope.launch {
            onResult(repository.countScriptsBySkinId(skinId))
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
