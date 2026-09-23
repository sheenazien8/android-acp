package com.lakasir.acp.ui.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lakasir.acp.data.local.ConnectionProfileEntity
import com.lakasir.acp.data.repository.AcpRepository
import com.lakasir.acp.data.repository.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ConnectionViewModel(private val repository: AcpRepository) : ViewModel() {

    val profiles: StateFlow<List<ConnectionProfileEntity>?> = repository.observeProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val connectionStates: StateFlow<Map<Long, ConnectionState>> = repository.connectionStates

    private val _form = MutableStateFlow<ProfileForm?>(null)
    val form: StateFlow<ProfileForm?> = _form.asStateFlow()

    private val _openProfile = MutableStateFlow<Long?>(null)
    val openProfile: StateFlow<Long?> = _openProfile.asStateFlow()

    private var awaitingProfileId: Long? = null

    init {
        viewModelScope.launch {
            repository.connectionStates
                .map { states -> states.values.filterIsInstance<ConnectionState.Connected>().map { it.profileId }.toSet() }
                .collect { connectedIds ->
                    awaitingProfileId?.let { id ->
                        if (id in connectedIds) {
                            awaitingProfileId = null
                            _openProfile.value = id
                        }
                    }
                }
        }
    }

    fun newProfile() {
        _form.value = ProfileForm()
    }

    fun editProfile(profile: ConnectionProfileEntity) {
        _form.value = ProfileForm.from(profile)
    }

    fun updateForm(transform: (ProfileForm) -> ProfileForm) {
        _form.value = _form.value?.let(transform)
    }

    fun dismissForm() {
        _form.value = null
    }

    fun saveForm() {
        val validated = _form.value?.validate() ?: return
        if (!validated.isValid) {
            _form.value = validated
            return
        }
        viewModelScope.launch {
            repository.saveProfile(validated.toEntity())
            _form.value = null
        }
    }

    fun connect(profile: ConnectionProfileEntity) {
        awaitingProfileId = profile.id
        repository.connect(profile)
    }

    fun disconnect(profile: ConnectionProfileEntity) {
        awaitingProfileId = null
        repository.disconnect(profile.id)
    }

    fun delete(profile: ConnectionProfileEntity) {
        viewModelScope.launch { repository.deleteProfile(profile) }
    }

    fun onProfileOpened() {
        _openProfile.value = null
    }
}
