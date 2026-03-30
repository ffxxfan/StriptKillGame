package com.example.striptkillgamedemo2.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleDTO {
    private String id;
    private String name;
    private String avatar;
    @JsonProperty("isNpc")
    private boolean isNpc;
    @JsonProperty("isAvailable")
    private boolean isAvailable;
}
