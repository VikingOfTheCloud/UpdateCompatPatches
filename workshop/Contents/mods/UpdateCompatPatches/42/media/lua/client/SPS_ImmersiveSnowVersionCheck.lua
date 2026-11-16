--
-- SPS Viewpoint Compatibility Patch - Immersive Snow - Viewpoint on Viewpoint 0.1.6.2.
--
-- DISCLAIMER: this only turns off Immersive Snow - Viewpoint's Viewpoint version check, and only
-- for Viewpoint 0.1.6.2 with bridge 1.3, so people can use it until its author updates. Nothing
-- else in Immersive Snow or the add-on is changed. Use at your own risk: the add-on's author
-- hasn't tested this Viewpoint version and SPS doesn't support it. Simple problems may get fixed
-- in this patch, but no promises. If you report a problem to the add-on's author, mention this
-- patch was on.
--
-- Why: Immersive Snow - Viewpoint (Workshop 3816586790, Java "bridge 1.3") has a fixed list of
-- Viewpoint versions it accepts, 0.1.6 and 0.1.6.1 (isnowvp.VP, read from Viewpoint's mod.info).
-- On 0.1.6.2, ISnowVP.present() is false, so Immersive Snow takes its snow out of Viewpoint's 3D
-- view and shows a "not supported" dialog once per game (ISnow_CompatViewpoint.lua). The add-on's
-- own way past that is ISnowVP.trust(true), which its log line suggests. This file calls it when
-- Viewpoint's mod.info says exactly 0.1.6.2 and ISnowVP.version() is exactly "1.3", before the
-- dialog's check on the first tick with a player. Any other pair is left to the add-on's own
-- check, so an update to either mod turns this off by itself. Delete this file once the add-on
-- accepts 0.1.6.2.
--
-- Checked read-only (Nov 2026): the bridge's 9 ZombieBuddy hooks, the 23 Viewpoint members it
-- uses directly and the ones it looks up by name all exist in 0.1.6.2 with the same types.
--

if isServer() then return end

local LOG = "[CompatPatches] "
local TESTED_VIEWPOINT = "0.1.6.2"
local TESTED_BRIDGE = "1.3"
local ADDON_ID = "ImmersiveSnowViewpoint"
local VIEWPOINT_ID = "Viewpoint"

local settled = false -- decided for this Lua session (trusted, or left alone)

local function isActive(id)
    local mods = getActivatedMods and getActivatedMods()
    return mods ~= nil and (mods:contains(id) or mods:contains("\\" .. id))
end

-- Viewpoint's version as its mod.info gives it (the same value the bridge checks), or nil.
local function viewpointVersion()
    if not getModInfoByID then return nil end
    local ids = { VIEWPOINT_ID, "\\" .. VIEWPOINT_ID }
    for i = 1, #ids do
        local ok, v = pcall(function()
            local info = getModInfoByID(ids[i])
            return info and info:getModVersion()
        end)
        if ok and v ~= nil then return tostring(v) end
    end
    return nil
end

local function check()
    if settled then return end
    local b = ISnowVP
    -- No table: the add-on's Java isn't loaded (yet). Try again at the next event; if it never
    -- loads, the add-on shows its own message as before.
    if type(b) ~= "table" or not b.trust or not b.version then return end
    settled = true
    if not isActive(ADDON_ID) then return end
    local bridge = tostring(b.version())
    local vp = viewpointVersion()
    if bridge == TESTED_BRIDGE and vp == TESTED_VIEWPOINT then
        b.trust(true)
        print(LOG .. "Immersive Snow - Viewpoint: version check skipped for Viewpoint " .. vp .. " (bridge " .. bridge
            .. "). Not tested by the add-on's author. Not supported by SPS.")
    else
        print(LOG .. "Immersive Snow - Viewpoint: left alone (Viewpoint " .. tostring(vp) .. ", bridge " .. bridge
            .. "); this patch only covers Viewpoint " .. TESTED_VIEWPOINT .. " with bridge " .. TESTED_BRIDGE)
    end
end

local function safeCheck()
    local ok, err = pcall(check)
    if not ok then
        settled = true
        print(LOG .. "Immersive Snow - Viewpoint version check error: " .. tostring(err))
    end
end

-- As early as possible: at load, at boot, and when the game starts (before the first tick, where
-- Immersive Snow decides whether to show its dialog).
safeCheck()
Events.OnGameBoot.Add(safeCheck)
Events.OnGameStart.Add(safeCheck)
