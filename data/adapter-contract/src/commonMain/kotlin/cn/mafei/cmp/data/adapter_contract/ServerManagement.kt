package cn.mafei.cmp.data.adapter_contract

import cn.mafei.cmp.core.domain.*

/** Account-bound server administration. Provider documents never enter Feature UI. */
interface ServerManagement {
    val currentDeviceId: String
    suspend fun isAdministrator(): Boolean
    suspend fun server(): ManagedServer
    suspend fun counts(): ManagedMediaCounts
    suspend fun users(): List<ManagedUser>
    suspend fun tasks(): List<ManagedTask>
    suspend fun sessions(nowEpochMs: Long): List<ManagedSession>
    suspend fun activity(offset: Int, limit: Int, minDate: String? = null, hasUserId: Boolean? = null): ManagedActivityPage
    suspend fun sessionCommand(id: String, command: ManagedSessionCommand)
    suspend fun sessionMessage(id: String, text: String)
    suspend fun startLibraryScan(): LibraryScanResult
    suspend fun startTask(id: String)
    suspend fun cancelTask(id: String)
    suspend fun devices(): List<ManagedDevice>
    suspend fun renameDevice(id: String, customName: String)
    suspend fun revokeDevice(id: String)
}
