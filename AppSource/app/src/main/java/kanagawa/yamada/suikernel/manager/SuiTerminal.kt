/*
 * SuiKernel Manager
 * Copyright (C) 2026 Kanagawa Yamada
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

package kanagawa.yamada.suikernel.manager

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kanagawa.yamada.suikernel.manager.ui.theme.CardDark
import kanagawa.yamada.suikernel.manager.ui.theme.GoogleSansFlex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SuiTerminalScreen(onNavigateBack: () -> Unit = {}) {
    BackHandler(onBack = onNavigateBack)

    val coroutineScope = rememberCoroutineScope()
    var commandInput by remember { mutableStateOf("") }
    var terminalLogs by remember {
        mutableStateOf(
            listOf(
                "SuiKernel Terminal Emulator [Root Shell]",
                "Type commands below or use quick action chips.",
                "---"
            )
        )
    }
    var isRunning by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    fun execute(cmd: String) {
        val trimmed = cmd.trim()
        if (trimmed.isEmpty()) return
        if (trimmed.equals("clear", ignoreCase = true)) {
            terminalLogs = emptyList()
            commandInput = ""
            return
        }

        commandInput = ""
        terminalLogs = terminalLogs + "# $trimmed"
        isRunning = true

        coroutineScope.launch(Dispatchers.IO) {
            val result = SuiKernelIoctl.runTerminalCommand(trimmed)
            withContext(Dispatchers.Main) {
                terminalLogs = terminalLogs + result
                isRunning = false
            }
        }
    }

    LaunchedEffect(terminalLogs.size) {
        if (terminalLogs.isNotEmpty()) {
            listState.animateScrollToItem(terminalLogs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 32.dp)
    ) {
        // Header matching SuiKernel Settings style
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_comet),
                contentDescription = "Comet Icon",
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            VerticalDivider(
                color = Color.White,
                modifier = Modifier
                    .height(48.dp)
                    .width(1.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = "Terminal Emulator",
                    fontFamily = GoogleSansFlex,
                    fontWeight = FontWeight.Medium,
                    fontSize = 20.sp,
                    color = Color.White
                )

                Text(
                    text = "This added because The Dev needs to check",
                    fontFamily = GoogleSansFlex,
                    fontWeight = FontWeight.Light,
                    fontSize = 12.sp,
                    color = Color(0xFFA1D477)
                )
            }
        }

        // Quick Command Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val quickCmds = listOf("id", "which ksud", "ksud -V", "ls -la /data/SuiKernel", "cat /proc/version", "clear")
            quickCmds.forEach { cmd ->
                SuggestionChip(
                    onClick = { execute(cmd) },
                    label = {
                        Text(
                            text = cmd,
                            fontFamily = GoogleSansFlex,
                            fontWeight = FontWeight.Normal,
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = CardDark
                    ),
                    border = SuggestionChipDefaults.suggestionChipBorder(
                        enabled = true,
                        borderColor = Color(0xFF30363D)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Output Console Box
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF0D1117), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF30363D), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(terminalLogs) { log ->
                    val isCmd = log.startsWith("# ")
                    Text(
                        text = log,
                        fontFamily = GoogleSansFlex,
                        fontWeight = if (isCmd) FontWeight.Medium else FontWeight.Normal,
                        fontSize = 13.sp,
                        color = if (isCmd) Color(0xFF58A6FF) else Color(0xFFE6EDF3),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
                if (isRunning) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = Color(0xFF56D364),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Running...",
                                fontFamily = GoogleSansFlex,
                                fontWeight = FontWeight.Normal,
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Input Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = commandInput,
                onValueChange = { commandInput = it },
                placeholder = {
                    Text(
                        text = "Enter command...",
                        fontFamily = GoogleSansFlex,
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { execute(commandInput) }),
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = GoogleSansFlex,
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.sp,
                    color = Color.White
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF58A6FF),
                    unfocusedBorderColor = Color(0xFF30363D),
                    focusedContainerColor = CardDark,
                    unfocusedContainerColor = CardDark
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = { execute(commandInput) },
                enabled = commandInput.isNotBlank() && !isRunning,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF238636),
                    disabledContainerColor = Color(0xFF21262D)
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Run",
                    fontFamily = GoogleSansFlex,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White
                )
            }
        }
    }
}
