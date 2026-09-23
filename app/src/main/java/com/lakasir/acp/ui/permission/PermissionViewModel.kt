package com.lakasir.acp.ui.permission

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lakasir.acp.data.repository.AcpRepository
import com.lakasir.acp.data.repository.PendingPermission
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PermissionViewModel(private val repository: AcpRepository) : ViewModel() {

    val current: StateFlow<PendingPermission?> = repository.pendingPermissions
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun answer(pending: PendingPermission, optionId: String?) {
        viewModelScope.launch { repository.answerPermission(pending, optionId) }
    }
}
