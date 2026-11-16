--
-- SPS Viewpoint Compatibility Patch - admin menu filter for Viewpoint's 1st/3rd-person view.
--
-- Viewpoint's action picker is built from the same right-click menu the classic view uses
-- (see ViewpointUI/Util/Harvest.lua). Several mods put admin and debug tools in that menu, and
-- in the picker every leaf of those trees becomes its own entry, burying the real actions.
-- While Viewpoint's view is on, this removes those mods' admin menus from the right-click menu
-- before the picker reads it. In the classic (isometric) view nothing is touched, so the tools
-- stay one camera switch away. Players who never see those menus are not affected.
--
-- Matched by each mod's own top-level label, translated by the game, so any language works:
--   Companion Dogs        IGUI_PD_DebugRoot              "Companion Dogs (debug)"
--   RC's Structures       ContextMenu_RCSF_Tools         "RC Structures"
--   Barrels Expanded      ContextMenu_BarrEx_AdminDebug  "Admin / Debug" (label starts with it)
--   LG Extended Plumbing  literal "LG Extended Plumbing"  (its debug-only menu)
--   Better Building Plus  ContextMenu_BBP_Admin          "BBP Admin" (on every world menu, zombies included)
--                         ContextMenu_BBP_FreePlace / _FreePlaceStop / _FreePlaceErase
--                         "Free Place (BBP)" and its stop/erase entries (admin free placing
--                         works in the classic view only; its key still toggles it)
-- Vanilla admin entries are left alone.
--

if isServer() then return end

local Filter = {}
local LOG = "[CompatPatches] "

-- True while Viewpoint's first- or third-person view is on.
local function viewOn()
    local V = ViewpointUI
    local B = V and V.Bridge
    if not (B and B.viewOn) then return false end
    local ok, on = pcall(B.viewOn)
    return ok and on == true
end

local function text(key)
    if getTextOrNull then
        local s = getTextOrNull(key)
        if type(s) == "string" and s ~= "" then return s end
    end
    return nil
end

local rules = nil
local function getRules()
    if rules then return rules end
    local exact, prefix = {}, {}
    local function add(list, s)
        if s then list[#list + 1] = s end
    end
    add(exact, text("IGUI_PD_DebugRoot"))
    add(exact, text("ContextMenu_RCSF_Tools"))
    add(exact, "LG Extended Plumbing")
    add(exact, text("ContextMenu_BBP_Admin"))
    add(exact, text("ContextMenu_BBP_FreePlace"))
    add(exact, text("ContextMenu_BBP_FreePlaceStop"))
    add(exact, text("ContextMenu_BBP_FreePlaceErase"))
    add(prefix, text("ContextMenu_BarrEx_AdminDebug"))
    rules = { exact = exact, prefix = prefix }
    return rules
end

local function isAdminMenu(name, r)
    if type(name) ~= "string" then return false end
    for i = 1, #r.exact do
        if name == r.exact[i] then return true end
    end
    for i = 1, #r.prefix do
        local p = r.prefix[i]
        if string.sub(name, 1, #p) == p then return true end
    end
    return false
end

-- Removes matching options from a menu and from its submenus (some admin menus, like
-- Barrels Expanded's, sit inside another mod menu). Returns how many were removed.
local function prune(menu, r, depth)
    if not menu or type(menu.options) ~= "table" or depth > 4 then return 0 end
    local drop, removed = {}, 0
    for i = 1, (menu.numOptions or 1) - 1 do
        local o = menu.options[i]
        if o then
            if isAdminMenu(o.name, r) then
                drop[#drop + 1] = o.name
            elseif o.subOption then
                removed = removed + prune(menu:getSubMenu(o.subOption), r, depth + 1)
            end
        end
    end
    for i = 1, #drop do
        menu:removeOptionByName(drop[i])
        removed = removed + 1
    end
    return removed
end

local function filter(context)
    if not context or not viewOn() then return end
    local ok, err = pcall(prune, context, getRules(), 0)
    if not ok then print(LOG .. "admin menu filter error: " .. tostring(err)) end
end

local function onFillWorld(playerNum, context, worldobjects, test)
    filter(context)
end

local function onAnimal(playerNum, context, animals, test)
    filter(context)
end

local function hook()
    if Filter.hooked then return end
    Filter.hooked = true
    Events.OnFillWorldObjectContextMenu.Add(onFillWorld)
    if Events.OnClickedAnimalForContext then
        Events.OnClickedAnimalForContext.Add(onAnimal)
    end
    print(LOG .. "admin menu filter ready (Viewpoint 1st/3rd-person view only)")
end

-- Handlers run in the order they were added, and other mods add theirs while loading or in
-- OnGameStart. Adding ours on the first tick after the game starts puts them after every one
-- of those, so the admin menus already exist when we look for them.
local function firstTick()
    Events.OnTick.Remove(firstTick)
    hook()
end

Events.OnGameStart.Add(function()
    Events.OnTick.Add(firstTick)
end)
