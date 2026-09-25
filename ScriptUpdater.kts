import com.darkan.bot.scripts.ScriptExecutor
import com.darkan.bot.scripts.getScriptsDirectory
import com.darkan.bot.scripts.withAction
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.zip.ZipInputStream

@ScriptDescription(
    author = "Capnarchie",
    name = "Script Updater",
    version = "1.0",
    description = "Downloads and updates bot scripts from any public GitHub or GitLab repository",
    category = ScriptCategory.OTHER
)
class ScriptUpdater : BotScript(), ConfigurableScript {

    val repoUrlConfig = StringConfigItem(
        name = "Repository URL",
        description = "Paste the GitHub or GitLab repository URL (e.g. https://github.com/owner/repo)",
        initialValue = ""
    ).withAction("Download / Update") {
        triggerSync()
        "Sync started"
    }

    val branchConfig = StringConfigItem(
        name = "Branch",
        description = "Repository branch to pull from (usually main or dev)",
        initialValue = "main"
    )

    val status = InfoDisplayConfigItem(
        name = "Status",
        description = "Current update status",
        initialValue = "Paste repository URL and click 'Download / Update'"
    )

    val lastUpdateDisplay = InfoDisplayConfigItem(
        name = "Last updated",
        description = "Timestamp of the last successful script update",
        initialValue = "Never"
    )

    val scriptsUpdatedDisplay = InfoDisplayConfigItem(
        name = "Scripts updated",
        description = "Number of scripts updated in the last sync",
        initialValue = "0"
    )

    @Volatile
    private var isUpdating = false

    override fun onStart() {
        status.value = "Starting manual sync..."
        triggerSync()
    }

    override suspend fun loop() {
        if (!isUpdating) {
            delay(1000)
        } else {
            delay(300)
        }
    }

    fun triggerSync() {
        if (isUpdating) {
            status.value = "Update already in progress..."
            return
        }

        val rawUrl = repoUrlConfig.value.trim()
        if (rawUrl.isBlank()) {
            status.value = "Error: Please paste a valid repository URL first!"
            return
        }

        val branch = branchConfig.value.trim().ifBlank { "main" }

        isUpdating = true
        status.value = "Connecting to repository..."

        Thread {
            try {
                performSync(rawUrl, branch)
            } catch (e: Exception) {
                status.value = "Error: ${e.message}"
                e.printStackTrace()
            } finally {
                isUpdating = false
            }
        }.apply {
            isDaemon = true
            name = "ScriptUpdater-Worker"
            start()
        }
    }

    private fun performSync(rawUrl: String, branch: String) {
        val zipUrls = resolveZipUrls(rawUrl, branch)
        if (zipUrls.isEmpty()) {
            status.value = "Error: Unsupported repository URL format"
            return
        }

        var downloadedStream: ZipInputStream? = null
        var chosenUrl = ""

        for (candidateUrl in zipUrls) {
            status.value = "Trying $candidateUrl..."
            val stream = fetchZipStream(candidateUrl)
            if (stream != null) {
                downloadedStream = stream
                chosenUrl = candidateUrl
                break
            }
        }

        if (downloadedStream == null) {
            status.value = "Error: Could not download zip from $rawUrl (checked branches: $branch, main, dev, master)"
            return
        }

        val scriptsDir: File = getScriptsDirectory()
        if (!scriptsDir.exists()) {
            scriptsDir.mkdirs()
        }

        status.value = "Extracting scripts into ${scriptsDir.name}..."
        var extractedCount = 0

        try {
            var entry = downloadedStream.nextEntry
            val buffer = ByteArray(8192)

            while (entry != null) {
                val entryName = entry.name.replace('\\', '/')
                // Skip the top-level directory in GitHub/GitLab zips (e.g. repo-main/...)
                val slashIdx = entryName.indexOf('/')
                val relativePath = if (slashIdx != -1) entryName.substring(slashIdx + 1) else entryName

                if (!entry.isDirectory && relativePath.endsWith(".kts", ignoreCase = true)) {
                    val targetFile = File(scriptsDir, relativePath)
                    val parent = targetFile.parentFile
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs()
                    }

                    var out: FileOutputStream? = null
                    try {
                        out = FileOutputStream(targetFile)
                        var bytesRead: Int
                        while (downloadedStream.read(buffer).also { bytesRead = it } != -1) {
                            out.write(buffer, 0, bytesRead)
                        }
                        out.flush()
                        extractedCount++
                    } finally {
                        out?.close()
                    }
                }
                downloadedStream.closeEntry()
                entry = downloadedStream.nextEntry
            }
        } finally {
            downloadedStream.close()
        }

        if (extractedCount == 0) {
            status.value = "Warning: No .kts script files found in repository archive"
            return
        }

        status.value = "Reloading scripts..."
        try {
            ScriptExecutor.loadScripts()
        } catch (e: Exception) {
            println("ScriptUpdater: ScriptExecutor.loadScripts() notice: ${e.message}")
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())
        status.value = "Done! Successfully updated $extractedCount script(s)."
        lastUpdateDisplay.value = timestamp
        scriptsUpdatedDisplay.value = "$extractedCount"
        println("ScriptUpdater: Successfully synced $extractedCount script(s) from $chosenUrl at $timestamp")
    }

    private fun resolveZipUrls(url: String, preferredBranch: String): List<String> {
        var clean = url.trim().removeSuffix("/").removeSuffix(".git")

        // Handle GitHub
        if (clean.contains("github.com", ignoreCase = true)) {
            // If user pasted tree URL like https://github.com/owner/repo/tree/branch
            val treeMatch = Regex("github\\.com/([^/]+)/([^/]+)/tree/([^/]+)").find(clean)
            if (treeMatch != null) {
                val owner = treeMatch.groupValues[1]
                val repo = treeMatch.groupValues[2]
                val treeBranch = treeMatch.groupValues[3]
                return listOf(
                    "https://github.com/$owner/$repo/archive/refs/heads/$treeBranch.zip"
                )
            }

            val repoMatch = Regex("github\\.com/([^/]+)/([^/]+)").find(clean)
            if (repoMatch != null) {
                val owner = repoMatch.groupValues[1]
                val repo = repoMatch.groupValues[2]
                val branches = listOf(preferredBranch, "main", "dev", "master").distinct()
                return branches.map { "https://github.com/$owner/$repo/archive/refs/heads/$it.zip" }
            }
        }

        // Handle GitLab
        if (clean.contains("gitlab.com", ignoreCase = true)) {
            val repoMatch = Regex("gitlab\\.com/([^/]+)/([^/]+)").find(clean)
            if (repoMatch != null) {
                val owner = repoMatch.groupValues[1]
                val repo = repoMatch.groupValues[2]
                val branches = listOf(preferredBranch, "main", "dev", "master").distinct()
                return branches.map { "https://gitlab.com/$owner/$repo/-/archive/$it/$repo-$it.zip" }
            }
        }

        // Direct zip URL
        if (clean.endsWith(".zip", ignoreCase = true)) {
            return listOf(clean)
        }

        return emptyList()
    }

    private fun fetchZipStream(urlStr: String, maxRedirects: Int = 5): ZipInputStream? {
        var currentUrl = urlStr
        var redirects = 0

        while (redirects < maxRedirects) {
            val connection = URI(currentUrl).toURL().openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Darkan-Script-Updater")
            connection.connectTimeout = 8000
            connection.readTimeout = 15000

            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location") ?: return null
                currentUrl = if (location.startsWith("http://") || location.startsWith("https://")) {
                    location
                } else {
                    URI(currentUrl).resolve(location).toString()
                }
                redirects++
                continue
            }

            if (code == 200) {
                return ZipInputStream(BufferedInputStream(connection.inputStream))
            }

            return null
        }
        return null
    }
}

ScriptUpdater()
