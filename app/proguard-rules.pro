# R8 shrinks and obfuscates release builds (isMinifyEnabled in app/build.gradle.kts). The libraries
# in use — Room, WorkManager, Coil, Media3, Compose, DataStore — ship their own consumer rules, and
# AGP's default file already keeps enum values()/valueOf() (settings are stored as enum names).

# Keep file names and line numbers so crash traces from Play Console de-obfuscate to real
# locations; the mapping file travels inside the uploaded AAB.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
