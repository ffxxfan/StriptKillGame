package com.example.striptkillgamedemo2.repository;

import com.example.striptkillgamedemo2.entity.mongo.Role;
import com.example.striptkillgamedemo2.entity.mongo.Script;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * 剧本 MongoDB 仓储接口。
 * <p>
 * 提供剧本模板的基本 CRUD 以及随机抽取能力，用于首页推荐、随机开局等场景。
 * </p>
 */
public interface ScriptRepository extends MongoRepository<Script, ObjectId> {

    /**
     * 随机抽取若干剧本。
     * <p>
     * 基于 MongoDB 聚合管道 {@code $sample} 实现，性能优于"查全量再随机"。
     * </p>
     *
     * @param count 需要抽取的剧本数量
     * @return 随机抽取的剧本列表
     */
    @Aggregation(pipeline = { "{ $sample: { size: ?0 } }" })
    List<Script> findRandomScripts(int count);

    /**
     * 根据剧本 ID 查找剧本。
     *
     * @param id 剧本主键
     * @return 匹配到的剧本；未找到时返回 {@code null}
     */
    Script findScriptById(ObjectId id);
}
