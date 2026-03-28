/**
 * SETTINGS
 * 1. Replace the ID below with your actual Google Sheet ID from the URL.
 * 2. Ensure the tab name matches exactly.
 */
const SPREADSHEET_ID = "1V_xuLitB-LKjKA0qHdTAps6ez76FHBbkv3AUegRM7o0";
const TARGET_SHEET_NAME = "Linkedin Job Tracker Sheet";

function getTargetSheet() {
  // Use openById to ensure the script finds the sheet during API calls
  const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  let sheet = ss.getSheetByName(TARGET_SHEET_NAME);

  if (!sheet) {
    sheet = ss.insertSheet(TARGET_SHEET_NAME);
    sheet.appendRow(["ID", "Job URL", "Company Name", "Job Title", "Description", "Status", "Last Updated", "Last Modified"]);
  }
  return sheet;
}

function doPost(e) {
  const lock = LockService.getScriptLock();
  try {
    lock.waitLock(10000);
    const sheet = getTargetSheet();
    const data = JSON.parse(e.postData.contents);

    // Normalize URL once for consistent matching.
    data.jobUrl = normalizeJobUrl(data.jobUrl);

    // Check if this is a delete or update request
    const action = e.parameter.action;

    if (action === 'deleteJob') {
      return handleDeleteJob(sheet, data);
    }

    if (action === 'updateJob') {
      return handleUpdateJob(sheet, data);
    }

    // Default: Upload new job (or update if URL exists)
    const lastRow = sheet.getLastRow();
    let existingRowIndex = -1;

    // Check for duplicates in Column B (Job URL)
    if (lastRow > 1) {
      const urls = sheet.getRange(2, 2, lastRow - 1, 1).getValues().flat();
      existingRowIndex = urls.indexOf(data.jobUrl);
    }

    if (existingRowIndex !== -1) {
      // Update existing row
      const row = existingRowIndex + 2;
      sheet.getRange(row, 3).setValue(data.companyName);
      sheet.getRange(row, 4).setValue(data.jobTitle || "");
      sheet.getRange(row, 5).setValue(data.jobDescription);
      sheet.getRange(row, 6).setValue(data.status);
      sheet.getRange(row, 7).setValue(new Date());
      // Set lastModified as a date object for proper formatting
      const lastModifiedDate = new Date(data.lastModified || new Date().getTime());
      sheet.getRange(row, 8).setValue(lastModifiedDate);
    } else {
      // Append new row
      const lastModifiedDate = new Date(data.lastModified || new Date().getTime());
      sheet.appendRow([
        data.id,
        data.jobUrl,
        data.companyName,
        data.jobTitle || "",
        data.jobDescription,
        data.status,
        new Date(),
        lastModifiedDate
      ]);
    }

    return ContentService.createTextOutput(JSON.stringify({"result":"success"}))
      .setMimeType(ContentService.MimeType.JSON);

  } catch (error) {
    return ContentService.createTextOutput(JSON.stringify({"result":"error", "message": error.toString()}))
      .setMimeType(ContentService.MimeType.JSON);
  } finally {
    lock.releaseLock();
  }
}

function handleUpdateJob(sheet, data) {
  try {
    const lastRow = sheet.getLastRow();

    if (lastRow <= 1) {
      // No jobs exist, can't update
      return ContentService.createTextOutput(JSON.stringify({"result":"error", "message": "No jobs to update"}))
        .setMimeType(ContentService.MimeType.JSON);
    }

    // Find the row by Job URL (Column B)
    const urls = sheet.getRange(2, 2, lastRow - 1, 1).getValues().flat().map(normalizeJobUrl);
    const rowIndex = urls.indexOf(data.jobUrl);

    if (rowIndex === -1) {
      return ContentService.createTextOutput(JSON.stringify({"result":"error", "message": "Job not found"}))
        .setMimeType(ContentService.MimeType.JSON);
    }

    // Update the row
    const row = rowIndex + 2;
    sheet.getRange(row, 3).setValue(data.companyName);
    sheet.getRange(row, 4).setValue(data.jobTitle || "");
    sheet.getRange(row, 5).setValue(data.jobDescription);
    sheet.getRange(row, 6).setValue(data.status);
    sheet.getRange(row, 7).setValue(new Date());
    // Set lastModified as a date object for proper formatting
    const lastModifiedDate = new Date(data.lastModified || new Date().getTime());
    sheet.getRange(row, 8).setValue(lastModifiedDate);

    return ContentService.createTextOutput(JSON.stringify({"result":"success", "message": "Job updated successfully"}))
      .setMimeType(ContentService.MimeType.JSON);

  } catch (error) {
    return ContentService.createTextOutput(JSON.stringify({"result":"error", "message": error.toString()}))
      .setMimeType(ContentService.MimeType.JSON);
  }
}

function handleDeleteJob(sheet, data) {
  try {
    const lastRow = sheet.getLastRow();

    if (lastRow <= 1) {
      return ContentService.createTextOutput(JSON.stringify({"result":"error", "message": "No jobs to delete"}))
        .setMimeType(ContentService.MimeType.JSON);
    }

    // Find the row by Job URL (Column B)
    const urls = sheet.getRange(2, 2, lastRow - 1, 1).getValues().flat().map(normalizeJobUrl);
    const rowIndex = urls.indexOf(data.jobUrl);

    if (rowIndex === -1) {
      return ContentService.createTextOutput(JSON.stringify({"result":"error", "message": "Job not found"}))
        .setMimeType(ContentService.MimeType.JSON);
    }

    // Delete the row (rowIndex + 2 because: +1 for header, +1 for 0-based to 1-based)
    sheet.deleteRow(rowIndex + 2);

    return ContentService.createTextOutput(JSON.stringify({"result":"success", "message": "Job deleted successfully"}))
      .setMimeType(ContentService.MimeType.JSON);

  } catch (error) {
    return ContentService.createTextOutput(JSON.stringify({"result":"error", "message": error.toString()}))
      .setMimeType(ContentService.MimeType.JSON);
  }
}

function doGet() {
  try {
    const sheet = getTargetSheet();
    const rows = sheet.getDataRange().getValues();

    if (rows.length <= 1) {
      return ContentService.createTextOutput(JSON.stringify([])).setMimeType(ContentService.MimeType.JSON);
    }

    const headers = rows[0];
    rows.shift(); // Remove headers

    // Filter out completely empty rows
    const validRows = rows.filter(row => {
      // Check if row has at least a job URL (column B / index 1)
      return row[1] && row[1].toString().trim() !== "";
    });

    // Detect if this is old format (without Job Title) or new format
    const hasJobTitleColumn = headers.length >= 8 && headers[3] === "Job Title";

    const json = validRows.map(row => {
      // Robust conversion for timestamp and lastModified
      const convertToTimestamp = (value) => {
        // Handle empty strings and null values
        if (!value || value === "") {
          return new Date().getTime();
        }
        if (typeof value === 'number') {
          return value; // Already a number
        } else if (value instanceof Date) {
          return value.getTime(); // Convert Date to milliseconds
        } else if (typeof value === 'string') {
          // Try parsing as Date string, otherwise return current time
          try {
            const parsedTime = new Date(value).getTime();
            return isNaN(parsedTime) ? new Date().getTime() : parsedTime;
          } catch (e) {
            return new Date().getTime();
          }
        } else {
          return new Date().getTime(); // Fallback to current time
        }
      };

      // Safe value getter with empty string fallback
      const getValue = (index, defaultValue = "") => {
        return row[index] !== undefined && row[index] !== null && row[index] !== ""
          ? row[index]
          : defaultValue;
      };

      // Safe ID converter - ensures it's always a valid number
      const getIdValue = (index) => {
        const val = row[index];
        if (val === undefined || val === null || val === "") {
          return 0;
        }
        if (typeof val === 'number') {
          return val;
        }
        if (typeof val === 'string') {
          const parsed = parseInt(val, 10);
          return isNaN(parsed) ? 0 : parsed;
        }
        return 0;
      };

      if (hasJobTitleColumn) {
        // New format with Job Title column
        return {
          id: getIdValue(0),
          jobUrl: getValue(1, ""),
          companyName: getValue(2, "Unknown Company"),
          jobTitle: getValue(3, ""),
          jobDescription: getValue(4, ""),
          status: getValue(5, "SAVED"),
          timestamp: convertToTimestamp(row[6]),
          lastModified: convertToTimestamp(row[7])
        };
      } else {
        // Old format without Job Title column - map columns differently
        return {
          id: getIdValue(0),
          jobUrl: getValue(1, ""),
          companyName: getValue(2, "Unknown Company"),
          jobTitle: "", // No job title in old format
          jobDescription: getValue(3, ""),
          status: getValue(4, "SAVED"),
          timestamp: convertToTimestamp(row[5]),
          lastModified: convertToTimestamp(row[6])
        };
      }
    });

    return ContentService.createTextOutput(JSON.stringify(json))
      .setMimeType(ContentService.MimeType.JSON);

  } catch (error) {
    return ContentService.createTextOutput(JSON.stringify({"error": error.toString()}))
      .setMimeType(ContentService.MimeType.JSON);
  }
}

function normalizeJobUrl(value) {
  return value ? value.toString().trim() : "";
}

/**
 * Migration Helper: Add Job Title column to existing sheet
 * Run this ONCE if you have existing data in the old format
 * This will insert a "Job Title" column between "Company Name" and "Description"
 */
function migrateToNewFormat() {
  try {
    const sheet = getTargetSheet();
    const headers = sheet.getRange(1, 1, 1, sheet.getLastColumn()).getValues()[0];

    // Check if Job Title column already exists
    if (headers.length >= 8 && headers[3] === "Job Title") {
      return "Migration not needed - Job Title column already exists";
    }

    // Insert a new column after "Company Name" (column C, so insert at position 4)
    sheet.insertColumnAfter(3);

    // Set the header for the new column
    sheet.getRange(1, 4).setValue("Job Title");

    // Fill existing rows with empty job titles
    const lastRow = sheet.getLastRow();
    if (lastRow > 1) {
      const emptyTitles = Array(lastRow - 1).fill([""]);
      sheet.getRange(2, 4, lastRow - 1, 1).setValues(emptyTitles);
    }

    return "Migration successful! Job Title column added at position D";
  } catch (error) {
    return "Migration failed: " + error.toString();
  }
}

