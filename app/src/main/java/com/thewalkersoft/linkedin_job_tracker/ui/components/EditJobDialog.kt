package com.thewalkersoft.linkedin_job_tracker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thewalkersoft.linkedin_job_tracker.data.JobEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditJobDialog(
    job: JobEntity,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var companyName by remember { mutableStateOf(job.companyName) }
    var jobUrl by remember { mutableStateOf(job.jobUrl) }
    var jobTitle by remember { mutableStateOf(job.jobTitle) }
    var jobDescription by remember { mutableStateOf(job.jobDescription) }

    var companyNameError by remember { mutableStateOf(false) }
    var jobUrlError by remember { mutableStateOf(false) }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Edit Job",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Form Fields
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .weight(1f, fill = false)
                ) {
                    // Company Name Field
                    OutlinedTextField(
                        value = companyName,
                        onValueChange = {
                            companyName = it
                            companyNameError = it.isBlank()
                        },
                        label = { Text("Company Name") },
                        isError = companyNameError,
                        supportingText = {
                            if (companyNameError) {
                                Text("Company name cannot be empty")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Job URL Field
                    OutlinedTextField(
                        value = jobUrl,
                        onValueChange = {
                            jobUrl = it
                            jobUrlError = it.isBlank()
                        },
                        label = { Text("Job URL") },
                        isError = jobUrlError,
                        supportingText = {
                            if (jobUrlError) {
                                Text("Job URL cannot be empty")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Job Title Field
                    OutlinedTextField(
                        value = jobTitle,
                        onValueChange = { jobTitle = it },
                        label = { Text("Job Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Job Description Field
                    OutlinedTextField(
                        value = jobDescription,
                        onValueChange = { jobDescription = it },
                        label = { Text("Job Description") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 150.dp),
                        minLines = 5,
                        maxLines = 10
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            // Validate fields
                            val isCompanyNameValid = companyName.isNotBlank()
                            val isJobUrlValid = jobUrl.isNotBlank()

                            companyNameError = !isCompanyNameValid
                            jobUrlError = !isJobUrlValid

                            if (isCompanyNameValid && isJobUrlValid) {
                                onSave(companyName, jobUrl, jobTitle, jobDescription)
                            }
                        }
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

