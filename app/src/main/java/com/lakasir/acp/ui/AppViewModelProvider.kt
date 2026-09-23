package com.lakasir.acp.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lakasir.acp.AcpApp
import com.lakasir.acp.di.AppContainer
import com.lakasir.acp.ui.connection.ConnectionViewModel
import com.lakasir.acp.ui.sessions.SessionsViewModel

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { ConnectionViewModel(container().repository) }
        initializer { SessionsViewModel(createSavedStateHandle(), container().repository) }
    }
}

private fun CreationExtras.container(): AppContainer = (this[APPLICATION_KEY] as AcpApp).container
