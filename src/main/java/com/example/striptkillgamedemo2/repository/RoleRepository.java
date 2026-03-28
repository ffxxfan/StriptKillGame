package com.example.striptkillgamedemo2.repository;

import com.example.striptkillgamedemo2.entity.mongo.Role;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface RoleRepository extends MongoRepository<Role, ObjectId> {

    List<Role> findByScriptId(ObjectId scriptId);
}
