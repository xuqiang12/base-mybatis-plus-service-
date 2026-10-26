package com.base.system.modules.family;

import com.base.common.exception.BusinessException;
import com.base.system.modules.family.FamilyModels.*;
import com.base.system.modules.family.mapper.FamilyMapper;
import com.base.system.modules.ledger.MoneyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.time.Instant;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import com.base.system.security.JwtSigningProperties;

// 仅管理家庭成员与邀请；账目业务由后续学习开发接入。
@Service
@RequiredArgsConstructor
@Transactional
public class FamilyService {
    private final FamilyMapper mapper;
    private final MoneyService moneyService;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final JwtSigningProperties signing;
    private static final String INVITATION_SUBJECT = "family-invitation";

    private String userName(int userId) {
        return mapper.userName(userId);
    }

    public void initialize(int userId) {
        if (mapper.currentFamily(userId) != null) return;
        if (mapper.lockUser(userId) == null) throw new BusinessException(401, "用户不存在或已停用");
        if (mapper.currentFamily(userId) != null) return;
        Family family = newFamily();
        mapper.insertMember(family.getId(), userId);
    }

    private Family current(int userId, String expected, boolean required) {
        initialize(userId);
        String id = mapper.currentFamily(userId);
        Family family = mapper.lockFamily(id);
        if (family == null
                || !id.equals(mapper.currentFamily(userId))
                || family.getHiddenOwner() != null)
            throw new BusinessException(409, "家庭已变化，请刷新后重试");
        if ((required && (expected == null || expected.isBlank()))
                || (expected != null && !expected.equals(id)))
            throw new BusinessException(409, "家庭已变化，请刷新后重试");
        return family;
    }

    public Snapshot state(int userId) {
        return snapshot(userId, current(userId, null, false));
    }

    public List<FamilyInfo> history(int userId) {
        return mapper.history(userId).stream().map(family -> {
            FamilyInfo info = new FamilyInfo();
            info.setId(family.getId());
            info.setName(family.getName());
            info.setMembers(List.of());
            info.setRecordCount(mapper.recordCount(family.getId()));
            info.setHasData(info.getRecordCount() > 0);
            return info;
        }).toList();
    }

    public Share share(int userId, String expected) {
        Family family = current(userId, expected, true);
        single(family.getId());
        var claims = JwtClaimsSet.builder().issuer(signing.issuer())
                .subject(INVITATION_SUBJECT).issuedAt(Instant.now())
                .claim("familyId", family.getId()).claim("inviterId", userId).build();
        var header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return new Share(encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue());
    }

    private Jwt invitation(String token) {
        try {
            Jwt invitation = decoder.decode(token);
            if (!INVITATION_SUBJECT.equals(invitation.getSubject())
                    || invitation.getClaimAsString("familyId") == null
                    || !(invitation.getClaim("inviterId") instanceof Number))
                throw new BusinessException(400, "邀请已失效，请让家人重新邀请");
            return invitation;
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(400, "邀请已失效，请让家人重新邀请");
        }
    }

    private Family invitedFamily(Jwt invitation) {
        String id = invitation.getClaimAsString("familyId");
        int inviter = ((Number) invitation.getClaim("inviterId")).intValue();
        Family family = mapper.family(id);
        if (family == null || family.getHiddenOwner() != null
                || !id.equals(mapper.currentFamily(inviter)) || mapper.members(id).size() != 1
                || userName(inviter) == null)
            throw new BusinessException(400, "邀请已失效，请让家人重新邀请");
        return family;
    }

    public InvitationInfo invitationInfo(String token) {
        Jwt invitation = invitation(token);
        Family family = invitedFamily(invitation);
        return new InvitationInfo(family.getName(), userName(((Number) invitation.getClaim("inviterId")).intValue()));
    }

    public Snapshot accept(int userId, String expected, String token) {
        initialize(userId);
        Jwt invitation = invitation(token);
        String mine = mapper.currentFamily(userId), theirs = invitation.getClaimAsString("familyId");
        int inviter = ((Number) invitation.getClaim("inviterId")).intValue();
        if (mine.equals(theirs) || inviter == userId)
            throw new BusinessException(400, "不能接受自己的邀请");
        lockFamilies(mine, theirs);
        invitedFamily(invitation);
        if (!mine.equals(mapper.currentFamily(userId)) || expected == null || !expected.equals(mine))
            throw new BusinessException(409, "家庭已变化，请刷新后重试");
        single(mine);
        mapper.archive(mine, userId);
        mapper.removeMember(userId);
        mapper.insertMember(theirs, userId);
        return snapshot(userId, mapper.family(theirs));
    }

    public Snapshot leave(int userId, String expected) {
        Family family = current(userId, expected, true);
        if (mapper.members(family.getId()).size() != 2)
            throw new BusinessException(400, "单人家庭无需退出");
        Family independent = newFamily();
        mapper.removeMember(userId);
        mapper.insertMember(independent.getId(), userId);
        return snapshot(userId, independent);
    }

    private void lockFamilies(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank())
            throw new BusinessException(400, "请选择家庭");
        for (String id : new TreeSet<>(List.of(a, b)))
            if (mapper.lockFamily(id) == null) throw new BusinessException(404, "家庭不存在");
    }

    private Family newFamily() {
        Family f = new Family();
        f.setId(UUID.randomUUID().toString());
        f.setName("我的家庭");
        mapper.insertFamily(f);
        moneyService.initializeCash(f.getId());
        return f;
    }

    private void single(String id) {
        if (mapper.members(id).size() != 1) throw new BusinessException(400, "此操作仅适用于单人家庭");
    }

    private Snapshot snapshot(int userId, Family family) {
        Snapshot state = new Snapshot();
        User user = new User();
        user.setId(String.valueOf(userId));
        user.setNickname(userName(userId));
        state.setCurrentUser(user);
        FamilyInfo info = new FamilyInfo();
        info.setId(family.getId());
        info.setName(family.getName());
        info.setMembers(mapper.members(family.getId()));
        info.setRecordCount(mapper.recordCount(family.getId()));
        info.setHasData(info.getRecordCount() > 0);
        state.setFamily(info);
        return state;
    }

}
