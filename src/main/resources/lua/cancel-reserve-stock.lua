
local productId = ARGV[1]

local tempOrderId = ARGV[2]

local orderId = ARGV[3]

local stockReserveKey = "product:stock:reserve:" ..productId

redis.call('hdel',stockReserveKey,tempOrderId)

redis.call('hdel','order:id:map:to:temp:id',orderId)

return 1