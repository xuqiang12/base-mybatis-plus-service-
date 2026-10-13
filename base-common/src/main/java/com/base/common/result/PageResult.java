package com.base.common.result;

import java.util.List;

// 与 Node 的分页数据结构保持一致。
public record PageResult<T>(List<T> list, long total, long pageNum, long pageSize) {}
