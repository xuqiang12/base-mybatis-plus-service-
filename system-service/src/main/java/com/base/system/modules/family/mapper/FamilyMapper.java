package com.base.system.modules.family.mapper;

import com.base.system.modules.family.FamilyModels.*;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface FamilyMapper {
    Family family(@Param("id") String id);

    Family lockFamily(@Param("id") String id);

    String currentFamily(@Param("userId") int userId);

    Integer lockUser(@Param("userId") int userId);

    List<User> members(@Param("familyId") String familyId);

    String userName(@Param("userId") int userId);

    int recordCount(@Param("familyId") String familyId);

    List<Family> history(@Param("userId") int userId);

    void archive(@Param("id") String id, @Param("userId") int userId);

    void insertFamily(Family family);

    void insertMember(@Param("familyId") String familyId, @Param("userId") int userId);

    void removeMember(@Param("userId") int userId);

}
