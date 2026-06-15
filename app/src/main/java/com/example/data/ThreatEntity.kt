package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "threat_records")
data class ThreatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,          // "SMS", "EMAIL", "URL"
    val content: String,       // The raw email/SMS/URL data
    val sender: String,        // Senders info (e.g. +18324... or "Manual")
    val riskLevel: String,     // "SAFE", "SUSPICIOUS", "DANGEROUS"
    val riskScore: Int,        // Score from 0 (Safe) to 100 (Unsafe)
    val analysisVerdict: String,// Text explanation of why it is flagged/safe
    val recommendedAction: String, // What the user should do
    val timestamp: Long = System.currentTimeMillis()
)
