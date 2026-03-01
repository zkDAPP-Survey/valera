package ui.views

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import data.SignatureRequest
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import ui.composables.Logo
import ui.composables.ScreenHeading
import ui.composables.buttons.NavigateUpButton
import ui.viewmodels.SignatureRequestsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureRequestsView(
    onClickBack: () -> Unit,
    onClickLogo: () -> Unit,
    viewModel: SignatureRequestsViewModel = koinViewModel()
) {
    val requests by viewModel.requests.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope() // Добавили корутин scope

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    ScreenHeading("Signature Requests")
                },
                navigationIcon = {
                    NavigateUpButton(onClickBack)
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshRequests() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    Logo(onClick = onClickLogo)
                    Spacer(Modifier.width(15.dp))
                }
            )
        }
    ) { scaffoldPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
        ) {
            if (requests.isEmpty()) {
                // Пустое состояние
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No signature requests",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Signature requests from surveys will appear here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Список запросов
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(requests) { request ->
                        SignatureRequestCard(
                            request = request,
                            onSign = {
                                scope.launch { // Запускаем в корутине
                                    viewModel.signRequest(request.id)
                                }
                            },
                            onReject = { viewModel.rejectRequest(request.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SignatureRequestCard(
    request: SignatureRequest,
    onSign: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Заголовок голосования
            Text(
                text = request.pollTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(4.dp))

            // ID голосования
            Text(
                text = "Poll ID: ${request.pollId}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            // Выбранный вариант
            Text(
                text = "Selected option:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(4.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = request.selectedOption,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            // Кнопки действий
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSign,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Sign")
                }

                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Reject")
                }
            }
        }
    }
}