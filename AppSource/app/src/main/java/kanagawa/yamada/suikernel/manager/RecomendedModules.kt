package kanagawa.yamada.suikernel.manager

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kanagawa.yamada.suikernel.manager.ui.theme.*

enum class ModuleStatus {
    DOWNLOAD,
    UPDATE,
    INSTALLED
}

data class RecomendedModuleItem(
    val title: String,
    val author: String,
    val version: String,
    val status: ModuleStatus,
    val url: String = ""
)

@Composable
fun RecomendedModulesScreen(onNavigateBack: () -> Unit = {}) {
    BackHandler(onBack = onNavigateBack)

    val modules = listOf(
        RecomendedModuleItem(
            title = "Brene SUSFS",
            author = "rrr333nnn333",
            version = "BRENE v0.0.69",
            status = ModuleStatus.DOWNLOAD
        ),
        RecomendedModuleItem(
            title = "BreZygisk",
            author = "rrr333nnn333",
            version = "BreZygisk 546",
            status = ModuleStatus.UPDATE
        ),
        RecomendedModuleItem(
            title = "BreWheel",
            author = "rrr333nnn333",
            version = "BreWheel 0.0.11+",
            status = ModuleStatus.INSTALLED
        ),
        RecomendedModuleItem(
            title = "Vector",
            author = "JingMatrix",
            version = "Vector v2.2",
            status = ModuleStatus.DOWNLOAD
        ),
        RecomendedModuleItem(
            title = "Always Strong",
            author = "evoker0",
            version = "v1.0.4",
            status = ModuleStatus.DOWNLOAD
        ),
        RecomendedModuleItem(
            title = "NoMount",
            author = "maxsteeel",
            version = "NoMount v2.0.0",
            status = ModuleStatus.DOWNLOAD
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            items(modules) { module ->
                ModuleRowItem(module = module)
            }
        }
    }
}

@Composable
fun ModuleRowItem(module: RecomendedModuleItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Title row
            when (module.status) {
                ModuleStatus.UPDATE -> {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = Color.White)) { append(module.title) }
                            append(" - ")
                            withStyle(SpanStyle(color = Color(0xFFE05A67))) { append("UPDATE") }
                        },
                        fontFamily = GoogleSansFlex,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
                ModuleStatus.INSTALLED -> {
                    Text(
                        text = module.title,
                        fontFamily = GoogleSansFlex,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color(0xFFA1D477)
                    )
                }
                ModuleStatus.DOWNLOAD -> {
                    Text(
                        text = module.title,
                        fontFamily = GoogleSansFlex,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Subtitle
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = Color.White)) { append("By: ${module.author} • ") }
                    withStyle(SpanStyle(color = Color(0xFF26C6DA))) { append(module.version) }
                },
                fontFamily = GoogleSansFlex,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp
            )
        }

        // Action icon button
        IconButton(
            onClick = { /* Mockup action */ },
            modifier = Modifier.size(36.dp)
        ) {
            when (module.status) {
                ModuleStatus.DOWNLOAD -> {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_download),
                        contentDescription = "Download ${module.title}",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(26.dp)
                    )
                }
                ModuleStatus.UPDATE -> {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_download_upd),
                        contentDescription = "Update ${module.title}",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(26.dp)
                    )
                }
                ModuleStatus.INSTALLED -> {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_ok),
                        contentDescription = "${module.title} Installed",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}
