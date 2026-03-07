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
redis.call('set',stockKey,currentQuantity-total)