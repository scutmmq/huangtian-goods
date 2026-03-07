local orderIds = ARGV    -- 获取要删除的订单 ID 列表

-- 校验 ZSet 键是否存在（可选，增强健壮性）
if not redis.call('exists', "order:timeout:trigger") then
    return 0  -- ZSet 不存在，直接返回删除数量 0
end

-- 批量删除 ZSet 中的订单 ID
redis.call('ZREM', "order:timeout:trigger", unpack(orderIds))

return 1