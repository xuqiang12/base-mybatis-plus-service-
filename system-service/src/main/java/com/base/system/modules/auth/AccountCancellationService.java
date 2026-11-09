package com.base.system.modules.auth;

import com.base.common.exception.BusinessException;
import com.base.security.LoginUser;
import com.base.system.modules.auth.mapper.AccountCancellationMapper;
import com.base.system.modules.user.UserService;
import com.base.system.modules.user.entity.UserEntity;
import com.base.system.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountCancellationService {
    private final UserMapper users;
    private final UserService userService;
    private final PasswordEncoder passwords;
    private final AccountCancellationMapper cancellation;

    @Transactional
    public void cancel(LoginUser principal, String password) {
        UserEntity user = users.selectById(principal.id());
        if (user == null || Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new BusinessException(401, "用户不存在或已停用");
        }
        if (user.getPassword() == null || !passwords.matches(password, user.getPassword())) {
            throw new BusinessException(401, "密码错误，请重新输入");
        }

        String familyId = cancellation.currentFamily(principal.id());
        userService.delete(principal.id(), principal);
        for (String hiddenFamilyId : cancellation.hiddenFamilies(principal.id())) {
            deleteFamily(hiddenFamilyId);
        }
        cancellation.clearHiddenOwner(principal.id());
        if (familyId != null) {
            if (cancellation.memberCount(familyId) > 1) {
                cancellation.anonymizeGiftAuthors(familyId, principal.id());
                cancellation.anonymizeLoanAuthors(familyId, principal.id());
                cancellation.anonymizeHouseholdAuthors(familyId, principal.id());
                cancellation.deleteMember(principal.id());
            } else {
                deleteFamily(familyId);
            }
        }
        if (cancellation.anonymizeUser(principal.id()) != 1) {
            throw new BusinessException(409, "账号状态已变化，请重试");
        }
    }

    private void deleteFamily(String familyId) {
        cancellation.deleteHouseholdItems(familyId);
        cancellation.deleteMoneyAdjustments(familyId);
        cancellation.deleteMoneyTransfers(familyId);
        cancellation.deleteGifts(familyId);
        cancellation.deleteLoans(familyId);
        cancellation.deleteBooks(familyId);
        cancellation.deleteFriends(familyId);
        cancellation.deleteHouseholdMonths(familyId);
        cancellation.deleteCategories(familyId);
        cancellation.deleteMoneyProjects(familyId);
        cancellation.deleteMoneyLogs(familyId);
        cancellation.deleteFamilyMembers(familyId);
        cancellation.deleteFamily(familyId);
    }
}
