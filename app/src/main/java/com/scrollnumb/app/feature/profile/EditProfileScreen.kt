package com.scrollnumb.app.feature.profile

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.scrollnumb.app.R
import com.scrollnumb.app.core.designsystem.components.NumbnessPill
import com.scrollnumb.app.core.designsystem.components.WarmGrainBackground
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbColors
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbCorners
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbType
import com.scrollnumb.app.core.util.AvatarHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: EditProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Gallery pick → move/zoom/crop to a circle, output written to a stable
    // file in app storage (survives cache eviction; readable by Coil via file://).
    val imageCropper = rememberLauncherForActivityResult(
        contract = CropImageContract()
    ) { result ->
        if (result.isSuccessful) {
            result.uriContent?.let { viewModel.onPhotoPicked(it.toString()) }
        }
    }

    // Shared by the bottom pill and the top-right Save: validate, save, toast.
    val doSave = save@{
        if (uiState.isSaving) return@save
        val email = uiState.email.trim()
        if (email.isNotEmpty() &&
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
        ) {
            Toast.makeText(
                context,
                context.getString(R.string.edit_profile_invalid_email),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            viewModel.save {
                Toast.makeText(
                    context,
                    context.getString(R.string.edit_profile_saved),
                    Toast.LENGTH_SHORT
                ).show()
                onSaved()
            }
        }
    }

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
                    .padding(horizontal = 12.dp, vertical = 2.dp),
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
                    text = stringResource(R.string.edit_profile_title),
                    style = ScrollNumbType.BrandDisplay,
                    fontWeight = FontWeight.Bold,
                    color = ScrollNumbColors.Cream,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = doSave, enabled = !uiState.isSaving) {
                    Text(
                        text = if (uiState.isSaving) stringResource(R.string.profile_saving)
                            else stringResource(R.string.edit_profile_save_short),
                        color = ScrollNumbColors.Saffron,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = ScrollNumbColors.Saffron)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .size(112.dp)
                            .clip(CircleShape)
                            .background(ScrollNumbColors.InkRaised)
                            .border(
                                width = 2.dp,
                                color = ScrollNumbColors.Saffron.copy(alpha = 0.4f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val avatar = uiState.avatar
                        val gender = context.getSharedPreferences("scroll_numb_prefs", android.content.Context.MODE_PRIVATE)
                            .getString("USER_GENDER", "Male") ?: "Male"
                        val defaultAvatar = painterResource(AvatarHelper.getDefaultAvatar(gender))
                        val desc = stringResource(R.string.edit_profile_title)
                        when {
                            avatar.startsWith("content://") || avatar.startsWith("file://") ->
                                coil.compose.AsyncImage(
                                    model = Uri.parse(avatar),
                                    contentDescription = desc,
                                    contentScale = ContentScale.Crop,
                                    error = defaultAvatar,
                                    fallback = defaultAvatar,
                                    modifier = Modifier.fillMaxSize()
                                )
                            avatar.isNotBlank() ->
                                Text(text = avatar, fontSize = 56.sp)
                            else ->
                                Image(
                                    painter = defaultAvatar,
                                    contentDescription = desc,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SmallPillButton(
                            label = stringResource(R.string.profile_pick_photo),
                            onClick = {
                                val output = Uri.fromFile(
                                    File(context.filesDir, "profile_avatar_${System.currentTimeMillis()}.png")
                                )
                                imageCropper.launch(
                                    CropImageContractOptions(
                                        uri = null,
                                        cropImageOptions = CropImageOptions(
                                            imageSourceIncludeGallery = true,
                                            imageSourceIncludeCamera = false,
                                            cropShape = CropImageView.CropShape.OVAL,
                                            fixAspectRatio = true,
                                            aspectRatioX = 1,
                                            aspectRatioY = 1,
                                            outputCompressFormat = Bitmap.CompressFormat.PNG,
                                            customOutputUri = output
                                        )
                                    )
                                )
                            }
                        )
                        SmallPillButton(
                            label = stringResource(R.string.profile_reset_photo),
                            onClick = viewModel::resetPhoto,
                            inverse = true
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = stringResource(R.string.edit_profile_avatar).uppercase(),
                        style = ScrollNumbType.Microcopy,
                        fontWeight = FontWeight.Bold,
                        color = ScrollNumbColors.CreamMuted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, bottom = 8.dp)
                    )

                    AvatarPicker(
                        selected = uiState.avatar,
                        onSelect = viewModel::onAvatarChange,
                        choices = viewModel.avatarChoices()
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    EditField(
                        label = stringResource(R.string.edit_profile_name),
                        value = uiState.displayName,
                        onValueChange = viewModel::onNameChange
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    EditField(
                        label = stringResource(R.string.edit_profile_username),
                        value = uiState.username,
                        onValueChange = viewModel::onUsernameChange
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    EditField(
                        label = stringResource(R.string.profile_email_label),
                        value = uiState.email,
                        onValueChange = viewModel::onEmailChange
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.edit_profile_dob),
                            style = ScrollNumbType.Microcopy,
                            fontWeight = FontWeight.SemiBold,
                            color = ScrollNumbColors.CreamMuted,
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                        )
                        com.scrollnumb.app.core.designsystem.components.DobPickerField(
                            iso = uiState.dob,
                            onIso = viewModel::onDobChange
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    EditField(
                        label = stringResource(R.string.edit_profile_bio),
                        value = uiState.bio,
                        onValueChange = viewModel::onBioChange,
                        singleLine = false,
                        minHeight = 96
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    NumbnessPill(
                        label = if (uiState.isSaving) stringResource(R.string.profile_saving)
                            else stringResource(R.string.edit_profile_save),
                        onClick = doSave,
                        enabled = !uiState.isSaving,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun SmallPillButton(
    label: String,
    onClick: () -> Unit,
    inverse: Boolean = false
) {
    val fill = if (inverse) ScrollNumbColors.Cream else ScrollNumbColors.Saffron
    val content = ScrollNumbColors.Ink
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(ScrollNumbCorners.pill))
            .background(fill)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = content
        )
    }
}

@Composable
private fun EditField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = true,
    minHeight: Int = 56
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = ScrollNumbType.Microcopy,
            fontWeight = FontWeight.SemiBold,
            color = ScrollNumbColors.CreamMuted,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            shape = RoundedCornerShape(ScrollNumbCorners.md),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = ScrollNumbColors.Cream,
                fontSize = 16.sp
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = ScrollNumbColors.InkRaised,
                unfocusedContainerColor = ScrollNumbColors.InkRaised,
                focusedBorderColor = ScrollNumbColors.Saffron,
                unfocusedBorderColor = ScrollNumbColors.InkLine,
                cursorColor = ScrollNumbColors.Saffron
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight.dp)
        )
    }
}

@Composable
private fun AvatarPicker(
    selected: String,
    onSelect: (String) -> Unit,
    choices: List<String>
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ScrollNumbCorners.lg))
            .background(ScrollNumbColors.InkRaised)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        choices.take(6).forEach { emoji ->
            val isSelected = emoji == selected
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) ScrollNumbColors.Saffron.copy(alpha = 0.18f)
                        else ScrollNumbColors.Ink
                    )
                    .border(
                        width = if (isSelected) 2.dp else 0.dp,
                        color = ScrollNumbColors.Saffron,
                        shape = CircleShape
                    )
                    .clickable { onSelect(emoji) },
                contentAlignment = Alignment.Center
            ) {
                Text(text = emoji, fontSize = 22.sp)
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ScrollNumbCorners.lg))
            .background(ScrollNumbColors.InkRaised)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        choices.drop(6).take(6).forEach { emoji ->
            val isSelected = emoji == selected
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) ScrollNumbColors.Saffron.copy(alpha = 0.18f)
                        else ScrollNumbColors.Ink
                    )
                    .border(
                        width = if (isSelected) 2.dp else 0.dp,
                        color = ScrollNumbColors.Saffron,
                        shape = CircleShape
                    )
                    .clickable { onSelect(emoji) },
                contentAlignment = Alignment.Center
            ) {
                Text(text = emoji, fontSize = 22.sp)
            }
        }
    }
}
