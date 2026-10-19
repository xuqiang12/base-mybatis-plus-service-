package com.base.common.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// 删除等操作共用的主键请求。
public record IdRequest(@NotNull(message = "请选择要操作的记录") @Positive(message = "记录编号无效") Integer id) {}
