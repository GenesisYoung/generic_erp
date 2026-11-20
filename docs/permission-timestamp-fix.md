# 页面内权限授权409：创建时间写入修复

对应请求：POST /api/permission/manipulate/permission。

运行日志已确认实际错误是 `Field 'create_date' doesn't have a default value`。page_permission_approvals_record.create_date为必填字段，但实体使用insertable=false，写入时没有包含该字段，数据库也没有默认值。通用数据约束异常处理将它统一转为409和“数据冲突”，因此页面无法显示真正失败原因。

修复：PagePermissionApprovalsRecord和存在同样配置的MenuRegisteredPermissionsRecord允许在INSERT中写入create_date，并通过@PrePersist在时间为空时填入创建时间；已有明确时间保留，更新时仍不覆盖创建时间。不更改权限范围、现有授权或数据库结构。

验证：后端63项测试通过，含新增4项真实ORM写入与数据库字段回读测试。测试使用隔离H2，不写入当前业务数据库。

生效方式：重新构建并重启后端，然后重新点击此前失败的授权开关。失败的写入没有成为有效授权，无需删除已存在的正常授权或修改数据库时间默认值。
