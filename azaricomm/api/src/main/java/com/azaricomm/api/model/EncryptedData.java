package com.azaricomm.api.model;

public class EncryptedData {
    private String patientPhone;
    private String subject;
    private String instructions;
    private String provider;

    // Default constructor for Jackson
    public EncryptedData() {}

    public EncryptedData(String patientPhone, String subject, String instructions, String provider) {
        this.patientPhone = patientPhone;
        this.subject = subject;
        this.instructions = instructions;
        this.provider = provider;
    }

    // Getters en Setters
    public String getPatientPhone() { return patientPhone; }
    public void setPatientPhone(String patientPhone) { this.patientPhone = patientPhone; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
}