package com.azaricomm.api.model;

public class EncryptedData {
    private String patientId;
    private String patientPhone;
    private String subject;
    private String instructions;
    private String provider;

    // Default constructor for Jackson
    public EncryptedData() {}

    public EncryptedData(String patientId, String patientPhone, String subject, String instructions, String provider) {
        this.patientId = patientId;
        this.patientPhone = patientPhone;
        this.subject = subject;
        this.instructions = instructions;
        this.provider = provider;
    }

    // Getters en Setters
    public String getPatientId() { return patientId; }
    public void setPatientId(String patientId) { this.patientId = patientId; }
    public String getPatientPhone() { return patientPhone; }
    public void setPatientPhone(String patientPhone) { this.patientPhone = patientPhone; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
}