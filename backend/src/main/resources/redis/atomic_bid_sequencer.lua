-- =============================================================================
-- LiveBid Distributed Auction Engine - Atomic Bidding Sequencer
-- Author: Jayavignesh (https://jayavignesh.dev)
-- =============================================================================
-- KEYS[1] = auction hash key (e.g. 'auction:1')
-- ARGV[1] = new bid amount (numeric string)
-- ARGV[2] = bidder ID (numeric string)
-- ARGV[3] = current epoch timestamp in ms
-- ARGV[4] = anti-snipe window in ms (e.g. 120000 = 2 min)
-- ARGV[5] = anti-snipe extension in ms (e.g. 120000 = 2 min)
-- =============================================================================

local exists = redis.call('EXISTS', KEYS[1])
if exists == 0 then
    return {0, 'NOT_INITIALIZED'}
end

local status = redis.call('HGET', KEYS[1], 'status')
if status ~= 'LIVE' then
    return {0, 'AUCTION_NOT_LIVE'}
end

local end_time_ms = tonumber(redis.call('HGET', KEYS[1], 'endTimeMs'))
local now_ms = tonumber(ARGV[3])
if now_ms >= end_time_ms then
    return {0, 'AUCTION_EXPIRED'}
end

local current_bid = tonumber(redis.call('HGET', KEYS[1], 'currentHighestBid'))
local new_bid = tonumber(ARGV[1])
if new_bid <= current_bid then
    return {0, 'BID_TOO_LOW', tostring(current_bid)}
end

-- Accept bid atomically
redis.call('HSET', KEYS[1], 'currentHighestBid', tostring(new_bid))
redis.call('HSET', KEYS[1], 'currentHighestBidderId', ARGV[2])

-- Anti-sniping check: if remaining time <= window, extend end time
local remaining = end_time_ms - now_ms
local snipe_window = tonumber(ARGV[4])
local extension = tonumber(ARGV[5])
local new_end_time = end_time_ms
if remaining <= snipe_window then
    new_end_time = end_time_ms + extension
    redis.call('HSET', KEYS[1], 'endTimeMs', tostring(new_end_time))
end

-- Increment atomic sequencer version
local new_version = redis.call('HINCRBY', KEYS[1], 'version', 1)

return {1, tostring(new_version), tostring(new_end_time), tostring(new_bid), ARGV[2]}

