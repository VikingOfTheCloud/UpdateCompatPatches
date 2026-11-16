# Working notes for Claude sessions

## Where the files live on the author's PC
- `%USERPROFILE%\Zomboid\UpdateCompatPatches-dev\` is the working copy of the Java source and tests. It maps to this repo's root.
- `%USERPROFILE%\Zomboid\Workshop\UpdateCompatPatches\` is the release folder the game uploads to Steam. It maps to `workshop/`.
  The Lua files exist only in the release folder (`...\42\media\lua\client\`). The release folder also holds the built jar and a copy of the source in `...\42\src\`. Neither of those is committed here.
- The PC folders are not git repos. To save work to GitHub, copy the changed files from the PC into a clone of this repo, then commit and push.

## Never commit
- Any `*.jar` or `*.idx`. That covers the built jar and the game, Viewpoint and ZombieBuddy jars used by the tests, which belong to other people.
- `old-local-*/` and `NOTES.md`: these are old builds and private dev notes.
- Commit author email must be the author's GitHub noreply address. Never use a personal email.

## Update routine (after a game or Viewpoint update)
1. Boot once. Search the client DebugLog for `not found`, and compare `[ZB] ZomboidFileSystem.loadMods(N mods)` with the previous session.
2. Version locks: add `unlock <id> <major.minor>` lines to `src/jay/compat/compat-rules.txt`.
3. API breaks: run `test/check2.py` against the new jars. Then prove the fixes offline with `test/jay/compat/Check.java` ("orig" vs "fixed").
4. Rebuild (`build.sh`). Copy the jar to the release folder's `42\media\java\client\` and the source to `42\src\`. Bump `modversion` in `42\mod.info`.
5. Upload from the game's Workshop menu. Then commit, tag `vX.Y.Z`, and create a GitHub Release with the jar attached.
