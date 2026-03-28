package com.example.striptkillgamedemo2.repository;

import com.example.striptkillgamedemo2.entity.mongo.GameRoom;
import com.example.striptkillgamedemo2.entity.enums.GameRoomStatus;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface GameRoomRepository extends MongoRepository<GameRoom, ObjectId> {

    List<GameRoom> findByStatus(GameRoomStatus status);
}
