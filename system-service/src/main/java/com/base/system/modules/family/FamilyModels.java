package com.base.system.modules.family;

import java.util.List;

// 家庭成员管理的请求与响应模型。
public class FamilyModels {
    @lombok.Data
    public static class Family {
        private String id;
        private String name;
        private Integer hiddenOwner;
    }

    public record Share(String token) {}

    @lombok.Data
    public static class User {
        private String id;
        private String nickname;
    }

    @lombok.Data
    public static class FamilyInfo {
        private String id;
        private String name;
        private List<User> members;
        private int recordCount;
        private boolean hasData;
    }

    @lombok.Data
    public static class Snapshot {
        private User currentUser;
        private List<User> localUsers = List.of();
        private FamilyInfo family;
    }

    public record InvitationInfo(String familyName, String inviterName) {}

    @lombok.Data
    public static class Request {
        private String token;
    }
}
