package com.scrollnumb.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scrollnumb.app.R
import com.scrollnumb.app.core.designsystem.components.WarmGrainBackground
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbColors
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbCorners
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbType

@Composable
fun PrivacyPolicyScreen(
    onBack: () -> Unit
) {
    val date = stringResource(R.string.legal_date_value)
    LegalScaffold(
        title = stringResource(R.string.legal_privacy_title),
        onBack = onBack
    ) {
        LegalIntroText(stringResource(R.string.legal_privacy_updated, date))
        Spacer(Modifier.height(16.dp))
        LegalParagraph(stringResource(R.string.legal_privacy_intro))
        LegalSection(stringResource(R.string.legal_privacy_h1), stringResource(R.string.legal_privacy_b1))
        LegalSection(stringResource(R.string.legal_privacy_h2), stringResource(R.string.legal_privacy_b2))
        LegalSection(stringResource(R.string.legal_privacy_h3), stringResource(R.string.legal_privacy_b3))
        LegalSection(stringResource(R.string.legal_privacy_h4), stringResource(R.string.legal_privacy_b4))
        LegalSection(stringResource(R.string.legal_privacy_h5), stringResource(R.string.legal_privacy_b5))
        LegalSection(stringResource(R.string.legal_privacy_h6), stringResource(R.string.legal_privacy_b6))
        LegalSection(stringResource(R.string.legal_privacy_h7), stringResource(R.string.legal_privacy_b7))
    }
}

@Composable
fun TermsOfServiceScreen(
    onBack: () -> Unit
) {
    val date = stringResource(R.string.legal_date_value)
    LegalScaffold(
        title = stringResource(R.string.legal_terms_title),
        onBack = onBack
    ) {
        LegalIntroText(stringResource(R.string.legal_terms_updated, date))
        Spacer(Modifier.height(16.dp))
        LegalParagraph(stringResource(R.string.legal_terms_intro))
        LegalSection(stringResource(R.string.legal_terms_h1), stringResource(R.string.legal_terms_b1))
        LegalSection(stringResource(R.string.legal_terms_h2), stringResource(R.string.legal_terms_b2))
        LegalSection(stringResource(R.string.legal_terms_h3), stringResource(R.string.legal_terms_b3))
        LegalSection(stringResource(R.string.legal_terms_h4), stringResource(R.string.legal_terms_b4))
        LegalSection(stringResource(R.string.legal_terms_h5), stringResource(R.string.legal_terms_b5))
        LegalSection(stringResource(R.string.legal_terms_h6), stringResource(R.string.legal_terms_b6))
    }
}

@Composable
private fun LegalScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScrollNumbColors.Ink)
    ) {
        WarmGrainBackground(modifier = Modifier.fillMaxSize())

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.login_close),
                        tint = ScrollNumbColors.Cream
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = title,
                    style = ScrollNumbType.BrandDisplay,
                    fontWeight = FontWeight.Bold,
                    color = ScrollNumbColors.Cream
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 4.dp)
            ) {
                Spacer(Modifier.height(8.dp))
                content()
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun LegalIntroText(text: String) {
    Text(
        text = text,
        style = ScrollNumbType.Microcopy,
        color = ScrollNumbColors.Saffron
    )
}

@Composable
private fun LegalParagraph(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = ScrollNumbColors.CreamDim
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun LegalSection(heading: String, body: String) {
    Spacer(Modifier.height(18.dp))
    Text(
        text = heading,
        style = ScrollNumbType.Microcopy,
        fontWeight = FontWeight.Bold,
        color = ScrollNumbColors.Saffron
    )
    Spacer(Modifier.height(6.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ScrollNumbCorners.md))
            .background(ScrollNumbColors.InkRaised)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = ScrollNumbColors.Cream
        )
    }
}
