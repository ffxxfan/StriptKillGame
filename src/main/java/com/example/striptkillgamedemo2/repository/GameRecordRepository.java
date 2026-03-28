package com.example.striptkillgamedemo2.repository;

import com.example.striptkillgamedemo2.entity.mongo.GameRecord;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface GameRecordRepository extends MongoRepository<GameRecord, ObjectId> {

    Optional<GameRecord> findByRoomId(ObjectId roomId);
}
