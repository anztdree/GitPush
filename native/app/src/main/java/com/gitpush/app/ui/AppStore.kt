package com.gitpush.app.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.gitpush.app.data.GhRepo
import com.gitpush.app.data.GhUser
import com.gitpush.app.data.HistoryEntry
import com.gitpush.app.data.Prefs

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

    /**
     * Posisi folder terakhir per repo+branch, kunci "fullName@branch" → path.
     * RepoScreen dicabut dari komposisi saat Viewer/Editor ditumpuk di atasnya —
     * peta ini menjaga posisi folder agar kembali dari lihat/edit file tidak
     * melompat balik ke root.
     */
    val lastRepoPath = HashMap<String, String>()

    var prefs: Prefs? = null
        private set

    fun init(p: Prefs) {
        prefs = p
        themeMode.value = p.themeMode
        defaultMsg.value = p.defaultCommitMsg
        history.value = p.history()
    }

    fun push(s: Screen) = stack.add(s)

    fun pop() {
        if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
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
