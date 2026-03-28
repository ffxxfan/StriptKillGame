package com.example.striptkillgamedemo2.dto;

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
    private boolean isNpc;
    private boolean isAvailable;
}
