package com.gsgd.generic_erp.enums;

/** Simplified Chinese user-facing messages for auth and error responses. */
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
    SEEESION_EXPIRED("会话已过期，请重新登录"),
    NAME_DULICATED("异常，名称重复!"),
    USERNAME_DULICATED("异常，用户名重复!"),
    EMAIL_DULICATED("异常，邮箱重复!"),
    CANT_DELETE_ROOT_USER("无法删除根用户"),
    ROLE_ASSIGNED("该角色已分配给用户，无法删除"),
    DEPT_ASSIGNED("该部门已分配给用户，无法删除");

    private final String value;

    Language_CN(String val) {
        this.value = val;
    }

    @Override
    public String getMessage() {
        return this.value;
    }
}
