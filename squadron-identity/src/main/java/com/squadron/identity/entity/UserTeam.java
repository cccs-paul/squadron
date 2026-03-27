package com.squadron.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_teams")
@IdClass(UserTeamId.class)
public class UserTeam {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Column(name = "team_id")
    private UUID teamId;

    @Builder.Default
    @Column(nullable = false)
    private String role = "MEMBER";
}
