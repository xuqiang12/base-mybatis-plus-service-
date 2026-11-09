package com.base.system.modules.auth.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface AccountCancellationMapper {

    @Select("SELECT family_id FROM relations.ledger_member WHERE user_id=#{userId}")
    String currentFamily(@Param("userId") int userId);

    @Select("SELECT COUNT(*) FROM relations.ledger_member WHERE family_id=#{familyId}")
    int memberCount(@Param("familyId") String familyId);

    @Select("SELECT id FROM relations.ledger_family WHERE hidden_owner=#{userId}")
    List<String> hiddenFamilies(@Param("userId") int userId);

    @Update("UPDATE relations.ledger_gift SET created_by_name='已注销用户' WHERE family_id=#{familyId} AND created_by=#{userId}")
    int anonymizeGiftAuthors(@Param("familyId") String familyId, @Param("userId") int userId);

    @Update("UPDATE relations.ledger_loan SET created_by_name='已注销用户' WHERE family_id=#{familyId} AND created_by=#{userId}")
    int anonymizeLoanAuthors(@Param("familyId") String familyId, @Param("userId") int userId);

    @Update("UPDATE relations.ledger_household_item SET created_by_name='已注销用户' WHERE family_id=#{familyId} AND created_by=#{userId}")
    int anonymizeHouseholdAuthors(@Param("familyId") String familyId, @Param("userId") int userId);

    @Update("UPDATE relations.ledger_family SET hidden_owner=NULL WHERE hidden_owner=#{userId}")
    int clearHiddenOwner(@Param("userId") int userId);

    @Delete("DELETE FROM relations.ledger_member WHERE user_id=#{userId}")
    int deleteMember(@Param("userId") int userId);

    @Delete("DELETE FROM relations.ledger_household_item WHERE family_id=#{familyId}")
    int deleteHouseholdItems(@Param("familyId") String familyId);

    @Delete("DELETE FROM relations.ledger_money_adjustment WHERE family_id=#{familyId}")
    int deleteMoneyAdjustments(@Param("familyId") String familyId);

    @Delete("DELETE FROM relations.ledger_money_transfer WHERE family_id=#{familyId}")
    int deleteMoneyTransfers(@Param("familyId") String familyId);

    @Delete("DELETE FROM relations.ledger_gift WHERE family_id=#{familyId}")
    int deleteGifts(@Param("familyId") String familyId);

    @Delete("DELETE FROM relations.ledger_loan WHERE family_id=#{familyId}")
    int deleteLoans(@Param("familyId") String familyId);

    @Delete("DELETE FROM relations.ledger_book WHERE family_id=#{familyId}")
    int deleteBooks(@Param("familyId") String familyId);

    @Delete("DELETE FROM relations.ledger_friend WHERE family_id=#{familyId}")
    int deleteFriends(@Param("familyId") String familyId);

    @Delete("DELETE FROM relations.ledger_household_month WHERE family_id=#{familyId}")
    int deleteHouseholdMonths(@Param("familyId") String familyId);

    @Delete("DELETE FROM relations.ledger_category WHERE family_id=#{familyId}")
    int deleteCategories(@Param("familyId") String familyId);

    @Delete("DELETE FROM relations.ledger_money_project WHERE family_id=#{familyId}")
    int deleteMoneyProjects(@Param("familyId") String familyId);

    @Delete("DELETE FROM relations.ledger_money_log WHERE family_id=#{familyId}")
    int deleteMoneyLogs(@Param("familyId") String familyId);

    @Delete("DELETE FROM relations.ledger_member WHERE family_id=#{familyId}")
    int deleteFamilyMembers(@Param("familyId") String familyId);

    @Delete("DELETE FROM relations.ledger_family WHERE id=#{familyId}")
    int deleteFamily(@Param("familyId") String familyId);

    @Update("UPDATE relations.sys_user SET email='deleted-' || CAST(id AS VARCHAR) || '@deleted.invalid', "
            + "username='deleted-' || CAST(id AS VARCHAR), nickname='已注销用户', phone=NULL, avatar=NULL, "
            + "password=NULL, email_verified=FALSE, is_deleted=TRUE, deleted_at=CURRENT_TIMESTAMP, "
            + "updated_at=CURRENT_TIMESTAMP WHERE id=#{userId} AND is_deleted=TRUE")
    int anonymizeUser(@Param("userId") int userId);
}
