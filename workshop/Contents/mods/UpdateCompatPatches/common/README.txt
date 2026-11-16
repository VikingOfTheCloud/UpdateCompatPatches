SPS Viewpoint Compatibility Patch 42.22.2 (mod version 0.2.3)
============================================================

Keeps mods running across Project Zomboid updates until their authors ship fixes. Nothing on disk is changed;
everything is applied in memory while the game loads, through ZombieBuddy.

1. Version unlock - the game skips a mod when the running game is above its mod.info versionMax
   (ChooseGameInfo.Mod.isAvailableSelf, comparing major.minor only: 42.21 covers every 42.21.x).
   "unlock <modId> 42.22" lets that mod load on games up to 42.22; on 42.23 the rule stops applying.
2. Class adapter - when a mod's Java class first loads, and only if it mentions something a rule covers,
   its references to moved/removed game code are rewritten in memory. A rule only switches on when the old
   class or method is really gone from the running game.

3. Admin menu filter (0.2.1, Lua: 42/media/lua/client/SPS_ViewpointAdminFilter.lua) - while
   Viewpoint's first- or third-person view is on, other mods' admin and debug menus are removed from
   the right-click menu before Viewpoint's action picker reads it: Companion Dogs (debug), RC Structures,
   Barrels Expanded's Admin / Debug submenu, LG Extended Plumbing's debug menu, and Better Building
   Plus's BBP Admin menu and Free Place entries (free placing only works in the classic view). Matched by each mod's
   translated label. The classic view is untouched, and vanilla admin entries are left alone.
   The jar's own log banner still says 0.2.0 because the jar did not change.

4. Immersive Snow - Viewpoint version check (0.2.3, Lua: 42/media/lua/client/SPS_ImmersiveSnowVersionCheck.lua) -
   the add-on (Workshop 3816586790, Java bridge 1.3) only accepts Viewpoint 0.1.6 and 0.1.6.1; on 0.1.6.2
   Immersive Snow takes its snow out of Viewpoint's 3D view and shows a "not supported" dialog. When
   Viewpoint's mod.info says exactly 0.1.6.2 and ISnowVP.version() is exactly 1.3, this calls the add-on's own
   ISnowVP.trust(true) before the dialog's first-tick check. Any other pair is left to the add-on's own check.
   DISCLAIMER: version check only; nothing else is changed. Not tested by the add-on's author and not
   supported here. Remove the file once the add-on accepts 0.1.6.2.

Rules: jay/compat/compat-rules.txt inside the jar (source copy in 42/src). For a quick local test, extra lines
can go in <Zomboid folder>/update-compat-patches.txt (same format, read after the built-in list).

  unlock <modId> <game major.minor>
  class  <old/Internal/Name> <new/Internal/Name> [interface]
  desc   <owner> <method> <descriptor after class rules> <new descriptor>
  static <owner> <method> <descriptor> <newOwner> <newMethod> <newDescriptor>

Load order: directly under ZombieBuddy. The adapter only rewrites classes that load after it starts.

Optional: ZombieBuddy can load this jar before the game starts (agent argument
patches_jar=<jar>:jay.compat, jar path without a drive letter). The unlock then also covers single player's
main-menu mod list, which the game builds before any mod-list Java runs.

Multiplayer: add to the server's WorkshopItems= and Mods=. The jar is client-only; a dedicated server does not
run it and checks versionMax on its own copies, so out-of-date mods also need versionMax raised in the server's
copies of their mod.info.

Log lines (console.txt / DebugLog, prefix [CompatPatches]):
  ready: N unlock(s), class adapter on (...)   started
  unlocked <id>: ...                           a capped mod was let in
  adapted <class>                              a class was rewritten as it loaded
  ... still exists in this game; off           a rule not needed on this game version
  Immersive Snow - Viewpoint: version check skipped ...   the override in 4. is on
  Immersive Snow - Viewpoint: left alone (...)            versions differ from the tested pair; off
