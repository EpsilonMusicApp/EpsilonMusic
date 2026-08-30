

package com.epsilonmusic.app.viewmodels

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epsilonmusic.app.App
import com.epsilonmusic.app.constants.AccountChannelHandleKey
import com.epsilonmusic.app.constants.AccountEmailKey
import com.epsilonmusic.app.constants.AccountNameKey
import com.epsilonmusic.app.constants.DataSyncIdKey
import com.epsilonmusic.app.constants.InnerTubeCookieKey
import com.epsilonmusic.app.constants.VisitorDataKey
import com.epsilonmusic.app.utils.SyncUtils
import com.epsilonmusic.app.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import androidx.datastore.preferences.core.edit

@HiltViewModel
class AccountSettingsViewModel @Inject constructor(
    private val syncUtils: SyncUtils,
) : ViewModel() {

    
    fun logoutAndClearSyncedContent(context: Context, onCookieChange: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            
            syncUtils.clearAllSyncedContent()

            
            App.forgetAccount(context)

            
            onCookieChange("")
        }
    }

    
    fun logoutKeepData(context: Context, onCookieChange: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            App.forgetAccount(context)
            withContext(Dispatchers.Main) {
                onCookieChange("")
            }
        }
    }

    
    fun saveTokenAndRestart(
        context: Context,
        cookie: String,
        visitorData: String,
        dataSyncId: String,
        accountName: String,
        accountEmail: String,
        accountChannelHandle: String,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.edit { settings ->
                settings[InnerTubeCookieKey] = cookie
                settings[VisitorDataKey] = visitorData
                settings[DataSyncIdKey] = dataSyncId
                settings[AccountNameKey] = accountName
                settings[AccountEmailKey] = accountEmail
                settings[AccountChannelHandleKey] = accountChannelHandle
            }
            withContext(Dispatchers.Main) {
                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                context.startActivity(intent)
                Runtime.getRuntime().exit(0)
            }
        }
    }
}
