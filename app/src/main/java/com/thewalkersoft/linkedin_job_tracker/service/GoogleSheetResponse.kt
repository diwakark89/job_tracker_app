package com.thewalkersoft.linkedin_job_tracker.service

import com.google.gson.annotations.SerializedName

/**
 * Response model for Google Apps Script API calls
 * Matches the JSON response from doPost() in GoogleSheetUpdateScript.gs
 */
data class GoogleSheetResponse(
    @SerializedName("result")
    val result: String? = null,

    @SerializedName("success")
    val success: Boolean? = null,

    @SerializedName("error")
    val error: String? = null,

    @SerializedName("message")
    val message: String? = null
)

