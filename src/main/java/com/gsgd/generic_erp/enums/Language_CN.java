package com.gsgd.generic_erp.enums;

public enum Language_CN implements Language {

    MULTIPLE_FAILURE("多次登录失败，账号已停用"),
    INVALID_CREDENTIALS("用户名或密码无效"),
    USER_DISABLED("用户已被禁用"),
    USER_NOT_AVAILABLE("用户不可用！"),
    LOGIN_SUCCESSFUL("登录成功"),
    TOKEN_REFRESH_SUCCESSFUL("令牌刷新成功"),
    TOKEN_REFRESH_FAILED("令牌刷新失败"),
    TOKEN_EXPIRED("令牌已过期"),
    INTERNAL_SERVER_ERROR("后台服务器错误"),
    SEEESION_EXPIRED("会话已过期，请重新登录");

    private final String value;

    Language_CN(String val) {
        this.value = val;
    }

    @Override
    public String getMessage() {
        return this.value;
    }
}
