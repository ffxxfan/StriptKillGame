package com.example.striptkillgamedemo2.entity.redis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;

import java.time.LocalDateTime;

/**
 * VoteRecord representing voting records during gameplay.
 * Stored in Redis List with key: game:{roomId}:votes
 *
 * Fields:
 * - id: Vote record identifier
 * - gameRoomId: Reference to the game room
 * - voterUserId: User ID of the voter
 * - stageNumber: Current stage number
 * - votedRoleId: Role ID being voted for
 * - voteCategory: Optional category/type of vote
 * - voteReason: Optional reason for the vote
 * - voteWeight: Optional vote weight for weighted voting
 * - timestamp: When the vote was cast
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoteRecord {
    private ObjectId id;
    private ObjectId gameRoomId;
    private ObjectId voterUserId;
    private int stageNumber;
    private ObjectId votedRoleId;
    private String voteCategory;
    private String voteReason;
    private Integer voteWeight;
    private LocalDateTime timestamp;
}