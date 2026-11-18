/*
 * SuiKernel Manager
 * Copyright (C) 2026 Kana Wesley Yamada
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package kana.wesley.yamada.suikernel.manager

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kana.wesley.yamada.suikernel.manager.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class HostPlatform {
    GITHUB,
    GITLAB
}

data class RecomendedModuleItem(
    val title: String,
    val author: String,
    val repo: String,
    val platform: HostPlatform = HostPlatform.GITHUB,
    val candidateIds: List<String> = emptyList()
)

fun findInstalledModule(
    item: RecomendedModuleItem,
    installedList: List<InstalledModuleInfo>
): InstalledModuleInfo? {
    // 1. Direct match with explicit candidateIds
    for (candidate in item.candidateIds) {
        val found = installedList.firstOrNull { it.id.equals(candidate, ignoreCase = true) }
        if (found != null) return found
    }

    // 2. Match with repo name (e.g. BRENE -> brene)
    val repoName = item.repo.substringAfter("/").trim()
    val repoClean = repoName.replace("-", "").replace("_", "").lowercase()
    val foundByRepo = installedList.firstOrNull {
        val idClean = it.id.replace("-", "").replace("_", "").lowercase()
        idClean == repoClean || it.id.equals(repoName, ignoreCase = true)
    }
    if (foundByRepo != null) return foundByRepo

    // 3. Match normalized title
    val titleClean = item.title.replace(" ", "").replace("-", "").lowercase()
    val foundByTitle = installedList.firstOrNull {
        val idClean = it.id.replace("-", "").replace("_", "").lowercase()
        val nameClean = it.name.replace(" ", "").replace("-", "").lowercase()
        idClean == titleClean || nameClean == titleClean
    }
    if (foundByTitle != null) return foundByTitle

    // 4. Fuzzy fallback match on name
    return installedList.firstOrNull {
        it.name.contains(item.title, ignoreCase = true) ||
        (item.candidateIds.isNotEmpty() && item.candidateIds.any { cand -> it.name.contains(cand, ignoreCase = true) })
    }
}

@Composable
fun RecomendedModulesScreen(onNavigateBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var isVanillaKernel by remember { mutableStateOf(false) }
    var installedModules by remember { mutableStateOf<List<InstalledModuleInfo>>(emptyList()) }

    val allModules = remember {
        listOf(
            RecomendedModuleItem(
                title = "Brene SUSFS",
                author = "rrr333nnn333",
                repo = "rrr333nnn333/BRENE",
                platform = HostPlatform.GITHUB,
                candidateIds = listOf("brene", "BRENE", "brene_susfs", "susfs", "susfs4ksu", "ksu_susfs")
            ),
            RecomendedModuleItem(
                title = "BreZygisk",
                author = "rrr333nnn333",
                repo = "rrr333nnn333/BreZygisk",
                platform = HostPlatform.GITHUB,
                candidateIds = listOf("brezygisk", "bre_zygisk", "rezygisk")
            ),
            RecomendedModuleItem(
                title = "BreWheel",
                author = "rrr333nnn333",
                repo = "rrr333nnn333/BreWheel",
                platform = HostPlatform.GITHUB,
                candidateIds = listOf("brewheel", "bre_wheel", "treat_wheel", "treatwheel")
            ),
            RecomendedModuleItem(
                title = "Vector",
                author = "JingMatrix",
                repo = "JingMatrix/Vector",
                platform = HostPlatform.GITHUB,
                candidateIds = listOf("zygisk_vector", "vector", "lsposed")
            ),
            RecomendedModuleItem(
                title = "HMA OSS",
                author = "frknkrc44",
                repo = "frknkrc44/HMA-OSS",
                platform = HostPlatform.GITHUB,
                candidateIds = listOf("hma_oss_zygisk", "hma_oss", "hma", "hide_my_applist", "hidemyapplist")
            ),
            RecomendedModuleItem(
                title = "Always Strong",
                author = "Evokerr",
                repo = "Evokerr/AlwaysStrong",
                platform = HostPlatform.GITLAB,
                candidateIds = listOf("alwaysstrong", "always_strong", "playintegrityfork")
            ),
            RecomendedModuleItem(
                title = "NoMount",
                author = "maxsteeel",
                repo = "maxsteeel/nomount",
                platform = HostPlatform.GITHUB,
                candidateIds = listOf("nomount", "no_mount")
            )
        )
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val versionStr = SuiKernelIoctl.readProcVersion() ?: ""
            val isVanilla = versionStr.contains("Vanilla", ignoreCase = true)
            val modules = SuiKernelIoctl.getInstalledModules()
            withContext(Dispatchers.Main) {
                isVanillaKernel = isVanilla
                installedModules = modules
            }
        }
    }

    val displayModules = remember(isVanillaKernel) {
        if (isVanillaKernel) {
            allModules.filterNot { it.title.equals("Brene SUSFS", ignoreCase = true) || it.repo.contains("BRENE", ignoreCase = true) }
        } else {
            allModules
        }
    }

    BackHandler(onBack = onNavigateBack)

    fun openModuleLink(module: RecomendedModuleItem) {
        val cleanRepo = module.repo.removePrefix("gitlab:").removePrefix("github:").trim()
            .removePrefix("https://gitlab.com/").removePrefix("https://github.com/").trimEnd('/')
        val webUrl = when (module.platform) {
            HostPlatform.GITHUB -> "https://github.com/$cleanRepo/releases"
            HostPlatform.GITLAB -> "https://gitlab.com/$cleanRepo/-/releases"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open browser: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 24.dp)
    ) {
        // Header
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Recomended Modules",
                fontFamily = GoogleSansFlex,
                fontWeight = FontWeight.Medium,
                fontSize = 28.sp,
                color = Color.White,
                textDecoration = TextDecoration.Underline
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = Color(0xFFE05A67))) { append("Enhance ") }
                    withStyle(SpanStyle(color = Color.White)) { append("Your ") }
                    withStyle(SpanStyle(color = Color(0xFF26C6DA))) { append("SuiKernel ") }
                    withStyle(SpanStyle(color = Color.White)) { append("Experience") }
                },
                fontFamily = GoogleSansFlex,
                fontWeight = FontWeight.Light,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Module list
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            displayModules.forEach { module ->
                val installedInfo = findInstalledModule(module, installedModules)
                ModuleRowItem(
                    module = module,
                    installedInfo = installedInfo,
                    onClick = { openModuleLink(module) }
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun ModuleRowItem(
    module: RecomendedModuleItem,
    installedInfo: InstalledModuleInfo? = null,
    onClick: () -> Unit
) {
    val isInstalled = installedInfo != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = module.title,
                    fontFamily = GoogleSansFlex,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.White
                )

                if (isInstalled) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_ok),
                        contentDescription = "Installed",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "By: ${module.author}",
                    fontFamily = GoogleSansFlex,
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp,
                    color = Color(0xFF9E9E9E)
                )

                if (isInstalled) {
                    Text(
                        text = "•",
                        fontFamily = GoogleSansFlex,
                        fontWeight = FontWeight.Normal,
                        fontSize = 12.sp,
                        color = Color(0xFF6B7280)
                    )
                    val versionText = installedInfo?.version?.takeIf { it.isNotBlank() }
                    Text(
                        text = if (versionText != null) "Installed ($versionText)" else "Installed",
                        fontFamily = GoogleSansFlex,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = Color(0xFFA1D477)
                    )
                    if (installedInfo?.enabled == false) {
                        Text(
                            text = "(Disabled)",
                            fontFamily = GoogleSansFlex,
                            fontWeight = FontWeight.Normal,
                            fontSize = 11.sp,
                            color = Color(0xFFE05A67)
                        )
                    }
                }
            }
        }

        IconButton(
            onClick = onClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_right_arrow),
                contentDescription = "Open ${module.title}",
                tint = Color(0xFF8E95A2),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
