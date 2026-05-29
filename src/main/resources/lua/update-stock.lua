local productId = ARGV[1]

local currentQuantity = tonumber(ARGV[2])

local stockReserveKey = "product:stock:reserve:" ..productId

local stockKey = "product:stock:available:" ..productId

local total = 0

local hashEntries =  redis.call('hgetall',stockReserveKey)

-- 获取预占总数
for i=2,#hashEntries,2 do
    local value = hashEntries[i]
    local num = tonumber(value) or 0
    total = total + num
end

-- 更新库存
local available = currentQuantity - total
if available < 0 then
    -- 预占总数超过实际库存，说明存在过期残留的预占记录，清除并重置
    redis.call('del', stockReserveKey)
    available = currentQuantity
end
redis.call('set', stockKey, available)