package io.nekohasekai.sfa.compose.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.database.RemoteServer
import io.nekohasekai.sfa.database.RemoteServerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRemoteServerScreen(navController: NavController, serverId: Long = -1L) {
    val isNewServer = serverId == -1L

    OverrideTopBar {
        TopAppBar(
            title = {
                Text(
                    stringResource(
                        if (isNewServer) {
                            R.string.remote_new_server
                        } else {
                            R.string.remote_edit_server
                        },
                    ),
                )
            },
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.content_description_back),
                    )
                }
            },
        )
    }

    val scope = rememberCoroutineScope()
    var origin by remember { mutableStateOf<RemoteServer?>(null) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var secretVisible by remember { mutableStateOf(false) }
    var urlError by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(!isNewServer) }

    LaunchedEffect(serverId) {
        if (!isNewServer) {
            val server = withContext(Dispatchers.IO) { RemoteServerManager.get(serverId) }
            if (server == null) {
                navController.navigateUp()
                return@LaunchedEffect
            }
            origin = server
            name = server.name
            url = server.url
            secret = server.secret
            isLoading = false
        }
    }

    if (isLoading) {
        return
    }

    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.profile_name)) },
            placeholder = { Text(stringResource(R.string.remote_optional)) },
            singleLine = true,
        )

        OutlinedTextField(
            value = url,
            onValueChange = {
                url = it
                urlError = false
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.profile_url)) },
            placeholder = { Text(stringResource(R.string.profile_input_required)) },
            isError = urlError,
            supportingText =
            if (urlError) {
                { Text(stringResource(R.string.remote_invalid_url, url)) }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )

        OutlinedTextField(
            value = secret,
            onValueChange = { secret = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.remote_secret)) },
            placeholder = { Text(stringResource(R.string.remote_optional)) },
            singleLine = true,
            visualTransformation =
            if (secretVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = { secretVisible = !secretVisible }) {
                    Icon(
                        imageVector =
                        if (secretVisible) {
                            Icons.Default.VisibilityOff
                        } else {
                            Icons.Default.Visibility
                        },
                        contentDescription = null,
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )

        Button(
            onClick = {
                val validatedURL = RemoteServer.validateURL(url)
                if (validatedURL == null) {
                    urlError = true
                    return@Button
                }
                scope.launch(Dispatchers.IO) {
                    val server = origin ?: RemoteServer()
                    server.name = name.trim()
                    server.url = validatedURL
                    server.secret = secret
                    if (origin != null) {
                        RemoteServerManager.update(server)
                    } else {
                        RemoteServerManager.create(server)
                    }
                    withContext(Dispatchers.Main) {
                        navController.navigateUp()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.save))
        }
    }
}
