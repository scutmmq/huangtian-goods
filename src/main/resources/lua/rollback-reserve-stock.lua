local productId = ARGV[1]

local tempOrderId = ARGV[2]

local orderId = ARGV[3]

local stockReserveKey = "product:stock:reserve:" ..productId

local stockKey = "product:stock:available:" ..productId

local quantity = tonumber(redis.call('hget',stockReserveKey,tempOrderId)) or 0

-- 回退添加库存
redis.call('incrby',stockKey,quantity)

-- 删除预占
redis.call('hdel',stockReserveKey,tempOrderId)

-- 删除映射表
redis.call('hdel','order:id:map:to:temp:id',orderId)

return 1