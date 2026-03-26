package com.example.striptkillgamedemo2.entity.mongo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * User entity representing player accounts in the system.
 *
 * Security Notes:
 * - Password field stores BCrypt-hashed passwords (use BCryptPasswordEncoder in service layer)
 * - Password is excluded from JSON serialization via @JsonIgnore
 * - Username is unique indexed for authentication
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
public class User {
    @Id
    private ObjectId id;

    @Indexed(unique = true)
    @NotBlank
    private String username;

    @NotBlank
    @JsonIgnore
    private String password;

    private String nickname;
    private String avatarUrl;

    @org.springframework.data.annotation.CreatedDate
    private LocalDateTime createdAt;
}
