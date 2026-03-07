local productId = ARGV[1]

local quantity = tonumber(ARGV[2])

local tempOrderId = ARGV[3]

local stockKey = "product:stock:available:" ..productId

local stockReserveKey = "product:stock:reserve:" ..productId

-- 查询当前库存
local stockQuantity = tonumber(redis.call('get',stockKey))

-- 判断当前库存是否小于0
if(stockQuantity<=0) then
    return 0
end

-- 判断当前库存是否足够
if(stockQuantity<quantity) then
    return 0 -- 不足够
end

-- 预占库存足够,扣减库存，预占库存

-- 扣减库存
redis.call("decrby",stockKey,quantity)

-- 预占库存
redis.call('hset',stockReserveKey,tempOrderId,quantity)

return 1