package com.verve.browser.presentation.browser.components.searchpill.pageactions

import android.graphics.Bitmap
import android.net.http.SslCertificate
import android.os.Build
import android.text.format.Formatter
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.verve.browser.R
import java.security.MessageDigest
import java.text.DateFormat
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private enum class SiteControlsPage {
    MAIN,
    CONNECTION_SECURITY,
    COOKIES_AND_DATA
}

private data class TlsConnectionDetails(
    val protocol: String = "TLS 1.3",
    val cipher: String = "AES_128_GCM",
    val keyExchange: String = "X25519"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteControlsBottomSheet(
    currentUrl: String,
    webView: WebView?,
    favicon: Bitmap? = webView?.favicon,
    isQuicConnection: Boolean = false,
    onOpenSettingsClick: () -> Unit = {},
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val parsedUri = remember(currentUrl) { runCatching { currentUrl.toUri() }.getOrNull() }
    val rawHost = remember(parsedUri) { parsedUri?.host.orEmpty() }
    val host = remember(rawHost, currentUrl) {
        rawHost.removePrefix("www.").takeIf { it.isNotBlank() } ?: currentUrl
    }
    val origin = remember(parsedUri) {
        val scheme = parsedUri?.scheme
        val authority = parsedUri?.authority
        if (!scheme.isNullOrBlank() && !authority.isNullOrBlank()) "$scheme://$authority" else ""
    }

    val isSecure = remember(parsedUri, webView) {
        parsedUri?.scheme.equals("https", ignoreCase = true) || webView?.certificate != null
    }
    val certificate: SslCertificate? = remember(webView, currentUrl) { webView?.certificate }

    var currentPage by remember { mutableStateOf(SiteControlsPage.MAIN) }
    var storedDataBytes by remember(currentUrl) { mutableLongStateOf(getCookieBytesForUrl(currentUrl)) }
    var tlsDetails by remember(rawHost) { mutableStateOf(TlsConnectionDetails()) }

    LaunchedEffect(rawHost, isSecure, currentPage) {
        if (isSecure && rawHost.isNotBlank() && currentPage == SiteControlsPage.CONNECTION_SECURITY) {
            inspectTlsConnection(rawHost)?.let { inspected ->
                tlsDetails = inspected
            }
        }
    }

    LaunchedEffect(currentUrl, origin, currentPage) {
        if (currentPage == SiteControlsPage.COOKIES_AND_DATA) {
            storedDataBytes = calculateSiteStorageBytes(url = currentUrl, origin = origin)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        dragHandle = null
    ) {
        BackHandler(enabled = currentPage != SiteControlsPage.MAIN) {
            currentPage = SiteControlsPage.MAIN
        }

        AnimatedContent(
            targetState = currentPage,
            transitionSpec = {
                if (targetState != SiteControlsPage.MAIN) {
                    (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it / 3 } + fadeOut())
                } else {
                    (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith
                            (slideOutHorizontally { it / 3 } + fadeOut())
                }
            },
            label = "siteControlsNavigation"
        ) { page ->
            when (page) {
                SiteControlsPage.MAIN -> {
                    SiteControlsMainContent(
                        host = host,
                        favicon = favicon,
                        isSecure = isSecure,
                        onConnectionClick = { currentPage = SiteControlsPage.CONNECTION_SECURITY },
                        onCookiesClick = { currentPage = SiteControlsPage.COOKIES_AND_DATA }
                    )
                }

                SiteControlsPage.CONNECTION_SECURITY -> {
                    ConnectionSecurityContent(
                        host = host,
                        isSecure = isSecure,
                        certificate = certificate,
                        tlsDetails = tlsDetails,
                        isQuicConnection = isQuicConnection,
                        onBackClick = { currentPage = SiteControlsPage.MAIN }
                    )
                }

                SiteControlsPage.COOKIES_AND_DATA -> {
                    CookiesAndSiteDataContent(
                        host = host,
                        favicon = favicon,
                        storedDataBytes = storedDataBytes,
                        onBackClick = { currentPage = SiteControlsPage.MAIN },
                        onSettingsClick = {
                            onDismissRequest()
                            onOpenSettingsClick()
                        },
                        onClearDataClick = {
                            clearSiteCookiesAndStorage(url = currentUrl, origin = origin)
                            storedDataBytes = 0L
                            webView?.reload()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SiteDomainHeader(
    host: String,
    favicon: Bitmap?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (favicon != null) {
            Image(
                bitmap = favicon.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        } else {
            Icon(
                imageVector = PageActionsSymbols.Public,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = host,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SiteControlsMainContent(
    host: String,
    favicon: Bitmap?,
    isSecure: Boolean,
    onConnectionClick: () -> Unit,
    onCookiesClick: () -> Unit
) {
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 12.dp)
    ) {
        SiteDomainHeader(host = host, favicon = favicon)

        HorizontalDivider(thickness = 1.dp, color = dividerColor)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onConnectionClick)
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isSecure) PageActionsSymbols.Lock else PageActionsSymbols.LockOpen,
                contentDescription = null,
                tint = if (isSecure) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(20.dp))

            Text(
                text = if (isSecure) "Connection is secure" else "Connection is not secure",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = if (isSecure) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }

        HorizontalDivider(thickness = 1.dp, color = dividerColor)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onCookiesClick)
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = PageActionsSymbols.Cookie,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(20.dp))

            Text(
                text = "Cookies and site data",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun CookiesAndSiteDataContent(
    host: String,
    favicon: Bitmap?,
    storedDataBytes: Long,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onClearDataClick: () -> Unit
) {
    val context = LocalContext.current
    val formattedSize = remember(storedDataBytes) {
        Formatter.formatShortFileSize(context, storedDataBytes)
    }

    val linkColor = MaterialTheme.colorScheme.primary
    val descriptionText = remember(linkColor) {
        buildAnnotatedString {
            append(
                "Cookies and other site data are used to remember you, for example to sign you in or to personalize ads. To manage cookies for all sites, see "
            )
            withLink(
                LinkAnnotation.Clickable(
                    tag = "settings",
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline
                        )
                    ),
                    linkInteractionListener = { onSettingsClick() }
                )
            ) {
                append("Settings")
            }
            append(".")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
    ) {
        // 1. Centered Favicon + Domain Header
        SiteDomainHeader(host = host, favicon = favicon)

        // 2. Back Navigation Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 20.dp, top = 2.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = PageActionsSymbols.ArrowBack,
                    contentDescription = "Back to site controls",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "Cookies and site data",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // 3. Description with underlined Settings link
        Text(
            text = descriptionText,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.5.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 14.dp)
        )

        // 4. Stored Data Size + Delete Action Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.database),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(20.dp))

            Text(
                text = "$formattedSize stored data",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = onClearDataClick,
                enabled = storedDataBytes > 0L
            ) {
                Icon(
                    imageVector = PageActionsSymbols.Delete,
                    contentDescription = "Clear cookies and site data",
                    tint = if (storedDataBytes > 0L) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    },
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun ConnectionSecurityContent(
    host: String,
    isSecure: Boolean,
    certificate: SslCertificate?,
    tlsDetails: TlsConnectionDetails,
    isQuicConnection: Boolean,
    onBackClick: () -> Unit
) {
    var showCertificateInfo by remember { mutableStateOf(false) }
    var showMeaningInfo by remember { mutableStateOf(false) }

    val issuerName = remember(certificate) {
        certificate?.issuedBy?.cName?.takeIf { it.isNotBlank() }
            ?: certificate?.issuedBy?.oName?.takeIf { it.isNotBlank() }
    }

    val bodyTextStyle = MaterialTheme.typography.bodyMedium.copy(
        fontSize = 14.5.sp,
        lineHeight = 20.sp
    )
    val linkTextStyle = MaterialTheme.typography.bodyMedium.copy(
        fontSize = 14.5.sp,
        fontWeight = FontWeight.Medium
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 20.dp, top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = PageActionsSymbols.ArrowBack,
                    contentDescription = "Back to site controls",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = if (isSecure) "Connection is secure" else "Connection is not secure",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 24.dp, top = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = if (isSecure) PageActionsSymbols.Lock else PageActionsSymbols.LockOpen,
                contentDescription = null,
                tint = if (isSecure) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp)
            )

            Spacer(modifier = Modifier.width(20.dp))

            if (isSecure) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = if (issuerName != null) {
                            "Verve verified that $issuerName issued this website's certificate."
                        } else {
                            "Verve verified this website's security certificate."
                        },
                        style = bodyTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "Certificate information",
                        style = linkTextStyle,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            showCertificateInfo = !showCertificateInfo
                        }
                    )

                    AnimatedVisibility(visible = showCertificateInfo) {
                        CertificateDetailsCard(
                            host = host,
                            certificate = certificate
                        )
                    }

                    Text(
                        text = "Your connection to $host is encrypted using a modern cipher suite.",
                        style = bodyTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = if (isQuicConnection) {
                            "The connection uses ${tlsDetails.protocol} over HTTP/3 (QUIC)."
                        } else {
                            "The connection uses ${tlsDetails.protocol}."
                        },
                        style = bodyTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "The connection is encrypted and authenticated using ${tlsDetails.cipher} and uses ${tlsDetails.keyExchange} as the key exchange mechanism.",
                        style = bodyTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "What do these mean?",
                        style = linkTextStyle,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            showMeaningInfo = !showMeaningInfo
                        }
                    )

                    AnimatedVisibility(visible = showMeaningInfo) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "TLS (${tlsDetails.protocol}) scrambles traffic between your device and $host so third parties cannot read or modify it.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${tlsDetails.cipher} encrypts and authenticates data packets, while ${tlsDetails.keyExchange} establishes a temporary session key with forward secrecy.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Your connection to $host is not encrypted.",
                        style = bodyTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "You should not enter any sensitive information on this site (for example, passwords or credit cards) because it could be stolen by attackers.",
                        style = bodyTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CertificateDetailsCard(
    host: String,
    certificate: SslCertificate?
) {
    val issuedToCn = certificate?.issuedTo?.cName?.takeIf { it.isNotBlank() } ?: host
    val issuedToOrg = certificate?.issuedTo?.oName?.takeIf { it.isNotBlank() }
    val issuedByCn = certificate?.issuedBy?.cName?.takeIf { it.isNotBlank() } ?: "Trusted Certificate Authority"
    val issuedByOrg = certificate?.issuedBy?.oName?.takeIf { it.isNotBlank() }
    val validFrom = certificate?.validNotBeforeDate?.let {
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(it)
    }
    val validUntil = certificate?.validNotAfterDate?.let {
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(it)
    }

    val sha256Fingerprint = remember(certificate) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                certificate?.x509Certificate?.encoded?.let { derBytes ->
                    MessageDigest.getInstance("SHA-256")
                        .digest(derBytes)
                        .joinToString(":") { "%02X".format(it) }
                }
            }.getOrNull()
        } else {
            null
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CertificateField(label = "Issued to", value = issuedToCn)
            if (issuedToOrg != null) {
                CertificateField(label = "Organization", value = issuedToOrg)
            }
            CertificateField(label = "Issued by", value = issuedByCn)
            if (issuedByOrg != null) {
                CertificateField(label = "Issuer organization", value = issuedByOrg)
            }
            if (validFrom != null) {
                CertificateField(label = "Issued on", value = validFrom)
            }
            if (validUntil != null) {
                CertificateField(label = "Expires on", value = validUntil)
            }
            if (sha256Fingerprint != null) {
                CertificateField(label = "SHA-256 fingerprint", value = sha256Fingerprint)
            }
        }
    }
}

@Composable
private fun CertificateField(
    label: String,
    value: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private suspend fun inspectTlsConnection(host: String): TlsConnectionDetails? =
    withContext(Dispatchers.IO) {
        runCatching {
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            (factory.createSocket(host, 443) as SSLSocket).use { socket ->
                socket.soTimeout = 2500
                val params = socket.sslParameters
                params.serverNames = listOf(SNIHostName(host))
                socket.sslParameters = params
                socket.startHandshake()

                val session = socket.session
                val rawProtocol = session.protocol.orEmpty()
                val formattedProtocol = when {
                    rawProtocol.equals("TLSv1.3", ignoreCase = true) -> "TLS 1.3"
                    rawProtocol.equals("TLSv1.2", ignoreCase = true) -> "TLS 1.2"
                    rawProtocol.isNotBlank() -> rawProtocol
                    else -> "TLS 1.3"
                }

                val suite = session.cipherSuite.orEmpty()
                val cipher = when {
                    suite.contains("AES_128_GCM") -> "AES_128_GCM"
                    suite.contains("AES_256_GCM") -> "AES_256_GCM"
                    suite.contains("CHACHA20_POLY1305") -> "CHACHA20_POLY1305"
                    else -> "AES_128_GCM"
                }
                val keyExchange = when {
                    formattedProtocol == "TLS 1.3" -> "X25519"
                    suite.contains("ECDHE_ECDSA") -> "ECDHE_ECDSA"
                    suite.contains("ECDHE_RSA") -> "ECDHE_RSA"
                    else -> "X25519"
                }

                TlsConnectionDetails(
                    protocol = formattedProtocol,
                    cipher = cipher,
                    keyExchange = keyExchange
                )
            }
        }.getOrNull()
    }

private fun getCookieBytesForUrl(url: String): Long {
    if (url.isBlank()) return 0L
    val raw = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull() ?: return 0L
    return raw.toByteArray().size.toLong()
}

private suspend fun calculateSiteStorageBytes(url: String, origin: String): Long {
    val cookieBytes = getCookieBytesForUrl(url)
    if (origin.isBlank()) return cookieBytes

    val webStorageBytes = withTimeoutOrNull(1500L.milliseconds) {
        suspendCancellableCoroutine { continuation ->
            runCatching {
                WebStorage.getInstance().getUsageForOrigin(origin) { usage ->
                    if (continuation.isActive) {
                        continuation.resume(usage)
                    }
                }
            }.onFailure {
                if (continuation.isActive) {
                    continuation.resume(0L)
                }
            }
        }
    } ?: 0L

    return cookieBytes + webStorageBytes
}

private fun clearSiteCookiesAndStorage(url: String, origin: String) {
    val cookieManager = CookieManager.getInstance()
    val rawCookies = runCatching { cookieManager.getCookie(url) }.getOrNull()
    if (!rawCookies.isNullOrBlank()) {
        rawCookies.split(';').forEach { cookie ->
            val name = cookie.substringBefore('=').trim()
            if (name.isNotEmpty()) {
                cookieManager.setCookie(
                    origin,
                    "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/"
                )
            }
        }
        cookieManager.flush()
    }
    if (origin.isNotBlank()) {
        runCatching { WebStorage.getInstance().deleteOrigin(origin) }
    }
}