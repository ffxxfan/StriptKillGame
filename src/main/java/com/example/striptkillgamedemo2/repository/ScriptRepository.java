package com.example.striptkillgamedemo2.repository;

import com.example.striptkillgamedemo2.entity.mongo.Script;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ScriptRepository extends MongoRepository<Script, ObjectId> {

    @Aggregation(pipeline = { "{ $sample: { size: ?0 } }" })
    List<Script> findRandomScripts(int count);
}
