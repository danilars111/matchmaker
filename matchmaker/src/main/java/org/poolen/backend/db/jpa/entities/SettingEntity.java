package org.poolen.backend.db.jpa.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "settings")
public class SettingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // We store the setting's name as a String. This allows us to use any of the enums
    // from the abstract Settings class (e.g., "HOUSE_BONUS", "SHEETS_ID").
    @Column(unique = true, nullable = false)
    private String name;

    @Column(nullable = false, length = 1024)
    private String description;

    // This is the solution to the generic <T>! We store every setting's value
    // as a String. The service layer will be responsible for converting it
    // back to the correct type (Integer, Boolean, etc.) when it's used.
    @Column(name = "setting_value", length = 1024)
    private String settingValue;

    // --- Constructors ---

    public SettingEntity() {}

    public SettingEntity(String name, String description, String settingValue) {
        this.name = name;
        this.description = description;
        this.settingValue = settingValue;
    }

    // --- Getters and Setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSettingValue() {
        return settingValue;
    }

    public void setSettingValue(String settingValue) {
        this.settingValue = settingValue;
    }
}

