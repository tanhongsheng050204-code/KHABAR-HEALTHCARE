package com.khabar.api.patients;

/**
 * What a patient has agreed to share with a family caregiver. SUMMARY permits the latest approved
 * take-home summary only. SUMMARY_AND_ALERTS also permits the patient card, active medication list,
 * and home readings; the caregiver may submit readings and maintain the shared medication list.
 */
public enum CaregiverScope {
    SUMMARY,
    SUMMARY_AND_ALERTS
}
