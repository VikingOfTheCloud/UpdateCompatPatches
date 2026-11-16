package jay.compat;

import me.zed_0xff.zombie_buddy.Patch;

/** ZombieBuddy inlines these advice bodies into zombie.gameStates.ChooseGameInfo$Mod. */
public class Patch_Unlock {

    @Patch(className = "zombie.gameStates.ChooseGameInfo$Mod", methodName = "isAvailable")
    public static class Available {
        @Patch.OnEnter
        public static void enter(@Patch.This Object self) {
            Unlock.beforeIsAvailable(self);
        }
    }

    @Patch(className = "zombie.gameStates.ChooseGameInfo$Mod", methodName = "isAvailableSelf")
    public static class Self {
        @Patch.OnExit
        public static void exit(@Patch.This Object self, @Patch.Return(readOnly = false) boolean available) {
            available = Unlock.afterIsAvailableSelf(self, available);
        }
    }
}
