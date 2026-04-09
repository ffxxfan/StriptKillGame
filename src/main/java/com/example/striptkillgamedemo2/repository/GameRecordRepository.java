package com.example.striptkillgamedemo2.repository;

import com.example.striptkillgamedemo2.entity.mongo.GameRecord;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * 游戏记录 MongoDB 仓储接口。
 * <p>
 * 提供对 {@link GameRecord} 的基础查询，用于战绩查看与复盘页。
 * </p>
 */
public interface GameRecordRepository extends MongoRepository<GameRecord, ObjectId> {

    /**
     * 根据游戏房间 ID 查找对应的战报记录。
     *
     * @param roomId 游戏房间 ID
     * @return 对应的战报；若该房间尚未生成战报则为 {@link Optional#empty()}
     */
    Optional<GameRecord> findByRoomId(ObjectId roomId);
}
