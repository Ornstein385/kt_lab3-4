package org.example.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Entity
@Table(name = "telegram_user")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TelegramUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @NotNull
    @Column(name = "telegram_user_id")
    private Long telegramUserId;

    @Column(name = "telegram_user_name")
    @Pattern(regexp = "^[a-zA-Z0-9_]{5,32}$")
    private String telegramUserName;

    //@Pattern(regexp = "[a-z]{2}")
    @Column(name = "locale", columnDefinition = "VARCHAR(2) DEFAULT ''")
    private String locale = "";

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "registration_time")
    private Date registrationTime;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "last_active_time")
    private Date lastActiveTime;

    @Min(0)
    @Max(255)
    @Column(name = "brightness_level")
    private Integer brightnessLevel;

    @Min(0)
    @Column(name = "small_details_remover")
    private Integer smallDetailsRemover;

    @Size(max = 20)
    @Column(name = "sheet_format", columnDefinition = "VARCHAR(20) DEFAULT 'A4'")
    private String sheetFormat = "A4";

    @Size(max = 120)
    @Column(name = "file_id")
    private String fileId;
}