# Kotlin code style

Pixzel follows the original Arc sources: Kotlin official style, four-space indentation, eight-space continuations, explicit imports and a 150-character right margin. Keep statements on separate lines. Use import aliases only when names collide.

Write every KDoc as a multiline block, including a one-sentence description. Separate logical stages of a function with blank lines, and explain nontrivial algorithms briefly. `verifyCodeStyle`, included in `check`, rejects single-line KDoc in live Kotlin sources and build scripts.

The root `.editorconfig` configures the editor. Import `arc-code-style.xml` through IntelliJ IDEA's Kotlin code-style settings to use the same formatter profile for both repositories. Run **Optimize Imports** and **Reformat Code** before committing. Fully qualified names inside strings, shader source and documentation references are intentional.

The installed IntelliJ command-line formatter also accepts this profile:

```powershell
& "$env:IDEA_HOME/bin/format.bat" -s tools/formatting/arc-code-style.xml -charset UTF-8 path/to/Source.kt
```
