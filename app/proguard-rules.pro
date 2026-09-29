# Project R8 rules. Libraries (Room, kotlinx.serialization, Compose, AndroidX) ship their own.

# Shrink and optimize, but keep class and method names: GymLog has no crash reporting, so a
# stack trace pasted into a bug report should be readable without a mapping file.
-dontobfuscate
