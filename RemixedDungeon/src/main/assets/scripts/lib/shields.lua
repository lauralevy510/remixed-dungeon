---
--- Generated by EmmyLua(https://github.com/EmmyLua)
--- Created by mike.
--- DateTime: 11/5/19 11:02 PM
---

local RPD = require "scripts/lib/commonClasses"

local shields = {}

local strForLevel    = {12,  14, 16, 18}
local chanceForLevel = {.3, .4, .4, .5}
local blockForLevel  = {4,   6,  8,  10}

---@param shieldLevel number
---@param itemLevel number
shields.blockDamage = function (shieldLevel, itemLevel)
    return blockForLevel[shieldLevel] * math.pow(1.3, itemLevel)
end

---@param shieldLevel number
---@param str number
shields.blockChance = function (shieldLevel, str)
    local weightPenalty = math.max(strForLevel[shieldLevel] - str, 0)
    return chanceForLevel[shieldLevel] * (1 - weightPenalty * 0.1)
end

---@param shieldLevel number
---@param str number
shields.rechargeTime = function(shieldLevel, str)
    local weightPenalty = math.max(strForLevel[shieldLevel] - str, 0)
    return 5 + weightPenalty
end

---@param shieldLevel number
---@param str number
shields.waitAfterBlockTime = function(shieldLevel, str)
    return math.max(str - strForLevel[shieldLevel], 0)
end

---@param baseDesc string
---@param shieldLevel number
---@param str number
shields.info = function(baseDesc, str, shieldLevel, itemLevel)

    local infoTemplate = RPD.textById("ShieldInfoTemplate")
    local strTemplate  = RPD.textById("ShieldStrTemplate")

    return RPD.textById(baseDesc)
            .. "\n\n"
            .. RPD.format(infoTemplate,
                          shields.blockDamage(shieldLevel, itemLevel),
                          shields.blockChance(shieldLevel, str) * 100,
                          shields.rechargeTime(shieldLevel, str))
            .. "\n\n"
            .. RPD.format(strTemplate, strForLevel[shieldLevel])
end

shields.makeShield = function(shieldLevel, shieldDesc)
    return {
        activate    = function(self, item, hero)

            local shieldBuff = RPD.affectBuff(hero,"ShieldLeft",
                                              shields.rechargeTime(shieldLevel,hero:effectiveSTR()))
            shieldBuff:level(shieldLevel)
            shieldBuff:setSource(item)
        end,

        deactivate  = function(self, item, hero)
            RPD.removeBuff(hero,"ShieldLeft")
        end,

        info        = function(self, item)
            local hero = RPD.Dungeon.hero --TODO fix me
            local str = hero:effectiveSTR()

            return shields.info(shieldDesc, str, shieldLevel ,item:level())
        end,

        typicalSTR  = function(self, item)
            return strForLevel[shieldLevel]
        end,

        requiredSTR = function(self, item)
            return strForLevel[shieldLevel]
        end
    }
end


return shields