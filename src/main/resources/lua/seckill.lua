-- 秒杀核心脚本：判断库存 + 一人一单 + 扣减库存（原子执行）
-- KEYS[1] = 库存 key       seckill:stock:{voucherId}
-- KEYS[2] = 已下单集合 key  seckill:order:{voucherId}
-- ARGV[1] = 用户 id
--
-- 返回值：
--   0 = 成功
--   1 = 用户已下过单
--   2 = 库存不足
--   3 = 库存未预热（活动未开始或 key 不存在）

-- 库存是否存在
local stock = redis.call('get',KEYS[1])
if stock == false then
    return 3
end

-- 库存是否充足
if tonumber(stock) <= 0 then
    return 2
end

-- 一人一单：判断用户是否已在集合里
if redis.call('sismember', KEYS[2],ARGV[1]) == 1 then
    return 1
end

-- 扣库存 + 记录用户
redis.call('decr', KEYS[1])
redis.call('sadd', KEYS[2],ARGV[1])

return 0