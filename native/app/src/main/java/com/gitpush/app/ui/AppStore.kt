package com.gitpush.app.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gitpush.app.data.GhRepo
import com.gitpush.app.data.GhUser
import com.gitpush.app.data.HistoryEntry
import com.gitpush.app.data.Prefs
import com.gitpush.app.TransferService

sealed class Screen {
    data class Repo(
        val owner: String,
        val name: String,
        val fullName: String,
        val defaultBranch: String,
        val isPrivate: Boolean
    ) : Screen()

    data class Viewer(
        val owner: String,
        val name: String,
        val branch: String,
        val path: String,
        val sha: String,
        val size: Long
    ) : Screen()

    data class Editor(
        val owner: String,
        val name: String,
        val branch: String,
        val basePath: String,
        val path: String?,
        val initial: String,
        val sha: String?
    ) : Screen()
}

/**
 * Satu proses global yang tampil sebagai dialog progres (unduh file, ZIP, pindah,
 * rename besar, dll). total == 0 → spinner tanpa persen; total > 0 → bar + persen.
 */
class OperationState {
    var running by mutableStateOf(false)
    var title by mutableStateOf("")
    var detail by mutableStateOf("")
    var done by mutableStateOf(0L)
    var total by mutableStateOf(0L)

    /** "bytes" → tampil formatBytes(done/total); lainnya → "d / t" */
    var unit by mutableStateOf("bytes")
}

object Store {
    val token = mutableStateOf("")
    val user = mutableStateOf<GhUser?>(null)
    val tab = mutableStateOf("home")
    val stack = mutableStateListOf<Screen>()
    val themeMode = mutableStateOf("dark")
    val defaultMsg = mutableStateOf("")
    val history = mutableStateOf(listOf<HistoryEntry>())
    val unread = mutableStateOf(0)
    val repos = mutableStateOf<List<GhRepo>>(emptyList())

    /** Dialog progres untuk SEMUA proses panjang (unduh, ZIP, pindah, hapus besar, dst). */
    val operation = OperationState()

    /**
     * Posisi folder terakhir per repo+branch, kunci "fullName@branch" → path.
     * RepoScreen dicabut dari komposisi saat Viewer/Editor ditumpuk di atasnya —
     * peta ini menjaga posisi folder agar kembali dari lihat/edit file tidak
     * melompat balik ke root.
     */
    val lastRepoPath = HashMap<String, String>()

    var prefs: Prefs? = null
        private set

    /** ApplicationContext — dipakai memulai notifikasi progres transfer di status bar. */
    var appCtx: Context? = null
        private set

    fun init(p: Prefs) {
        prefs = p
        themeMode.value = p.themeMode
        defaultMsg.value = p.defaultCommitMsg
        history.value = p.history()
    }

    /** Simpan applicationContext (dipanggil sekali dari MainActivity). */
    fun attach(c: Context) {
        appCtx = c.applicationContext
    }

    fun push(s: Screen) = stack.add(s)

    fun pop() {
        if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
    }

    // ---- Dialog progres global ----

    fun showOp(title: String, detail: String = "", unit: String = "bytes") {
        operation.title = title
        operation.detail = detail
        operation.unit = unit
        operation.done = 0L
        operation.total = 0L
        operation.running = true
        // Progress bar di STATUS BAR (unduh/ZIP/proses panjang lain) — selaras unggahan
        try { appCtx?.let { TransferService.start(it) } } catch (_: Exception) { }
    }

    fun opDetail(d: String) {
        if (operation.running) operation.detail = d
    }

    fun opProgress(done: Long, total: Long) {
        if (operation.running && total > 0) {
            operation.done = done
            operation.total = total
        }
    }

    fun opStep(done: Long, total: Long, detail: String? = null) {
        if (!operation.running) return
        operation.done = done
        operation.total = total
        if (detail != null) operation.detail = detail
    }

    fun hideOp() {
        operation.running = false
    }

    fun gotoTab(t: String) {
        stack.clear()
        tab.value = t
    }

    fun saveTheme(v: String) {
        themeMode.value = v
        prefs?.themeMode = v
    }

    fun saveDefaultMsg(v: String) {
        defaultMsg.value = v
        prefs?.defaultCommitMsg = v
    }

    fun log(kind: String, label: String, repo: String) {
        prefs?.addHistory(HistoryEntry(kind, label, repo, System.currentTimeMillis()))
        history.value = prefs?.history() ?: history.value
    }

    fun clearHistory() {
        prefs?.clearHistory()
        history.value = emptyList()
    }

    fun signOut() {
        prefs?.token = ""
        token.value = ""
        user.value = null
        stack.clear()
        tab.value = "home"
        unread.value = 0
        repos.value = emptyList()
    }
}
